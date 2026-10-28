package com.ramz.railora.ui.spot

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ramz.railora.R
import com.ramz.railora.domain.Placement
import com.ramz.railora.domain.TrainPlacement
import com.ramz.railora.domain.model.LiveTrainStatus
import com.ramz.railora.domain.model.TrainRoute
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.InfoWindow

/**
 * OpenStreetMap with the route as a line, halts as dots and the train as a marker with its
 * callout. Centres on the train the first time it's known for a train; after that the user's
 * pan/zoom is left alone until they tap the centre button.
 */
@Composable
fun TrainMapView(
    status: LiveTrainStatus,
    route: TrainRoute,
    placement: Placement,
    callout: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(10.0)
        }
    }
    val centredFor = remember { arrayOfNulls<String>(1) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.onPause()
        }
    }

    val trainPoint = placement.location?.let { GeoPoint(it.lat, it.lng) }

    Box(modifier) {
        AndroidView(factory = { map }, update = { view ->
            InfoWindow.closeAllInfoWindowsOn(view)
            view.overlays.clear()
            val located = route.points.filter { it.location != null }
            view.overlays += Polyline(view).apply {
                setPoints(located.map { GeoPoint(it.location!!.lat, it.location!!.lng) })
                outlinePaint.color = AndroidColor.rgb(0x1E, 0x3A, 0xC8)
                outlinePaint.strokeWidth = 9f
                isGeodesic = false
            }
            val halts = status.stops.map { it.station.code }.toSet()
            val dot = ContextCompat.getDrawable(view.context, R.drawable.map_stop_dot)
            located.filter { it.isStop || it.station.code in halts }.forEach { p ->
                view.overlays += Marker(view).apply {
                    position = GeoPoint(p.location!!.lat, p.location!!.lng)
                    icon = dot
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "${p.station.name} (${p.station.code})"
                }
            }
            if (trainPoint != null) {
                val train = Marker(view).apply {
                    position = trainPoint
                    icon = trainIcon(view.context.resources, headingWest(route, placement.kmNow))
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = callout
                }
                view.overlays += train
                train.showInfoWindow()
                if (centredFor[0] != status.number) {
                    centredFor[0] = status.number
                    view.controller.setZoom(11.5)
                    view.controller.setCenter(trainPoint)
                }
            } else if (centredFor[0] != status.number && located.isNotEmpty()) {
                centredFor[0] = status.number
                val first = located.first().location!!
                view.controller.setCenter(GeoPoint(first.lat, first.lng))
            }
            view.invalidate()
        })

        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapButton {
                IconButton(onClick = { trainPoint?.let { map.controller.animateTo(it) } }) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "Centre on train")
                }
            }
            MapButton {
                Column {
                    IconButton(onClick = { map.controller.zoomIn() }) { Icon(Icons.Rounded.Add, contentDescription = "Zoom in") }
                    HorizontalDivider(Modifier.width(48.dp))
                    IconButton(onClick = { map.controller.zoomOut() }) { Icon(Icons.Rounded.Remove, contentDescription = "Zoom out") }
                }
            }
        }
        // OpenStreetMap's licence asks for this credit on the map.
        Text(
            "© OpenStreetMap contributors",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.White.copy(alpha = 0.75f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = Color.DarkGray,
        )
    }
}

/** The train picture (facing right), about 64 dp wide, mirrored when the train heads west. */
private fun trainIcon(res: Resources, mirror: Boolean): BitmapDrawable {
    val src = BitmapFactory.decodeResource(res, R.drawable.map_train)
    val w = (64 * res.displayMetrics.density).toInt()
    val h = w * src.height / src.width
    val m = Matrix().apply {
        postScale(w.toFloat() / src.width * (if (mirror) -1 else 1), h.toFloat() / src.height)
    }
    return BitmapDrawable(res, Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true))
}

/** Whether the line runs westward where the train is (a few km either side). */
private fun headingWest(route: TrainRoute, km: Double?): Boolean {
    if (km == null) return false
    val behind = TrainPlacement.locate(route, km - 3) ?: return false
    val ahead = TrainPlacement.locate(route, km + 3) ?: return false
    return ahead.lng < behind.lng
}

@Composable
private fun MapButton(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, contentColor = Color(0xFF111827), shadowElevation = 4.dp) {
        Box(Modifier.width(48.dp)) { content() }
    }
}

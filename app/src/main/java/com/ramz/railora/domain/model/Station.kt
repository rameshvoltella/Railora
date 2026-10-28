package com.ramz.railora.domain.model

/**
 * The app's own models live in this package. Every site we read (NTES today, others later) is
 * parsed into these types, and screens only ever see these types — so changing or swapping a
 * site means changing its parser in `data/<site>/`, never the models or the UI.
 */

/** A station. [name] is display-ready ("Ernakulam Jn"), [code] upper-case ("ERS"). */
data class Station(val code: String, val name: String) {
    val label: String get() = "$name ($code)"
}

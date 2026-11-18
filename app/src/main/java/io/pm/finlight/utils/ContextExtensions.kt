package io.pm.finlight.utils

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity

fun Context.findActivity(): ComponentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

fun Context.findFragmentActivity(): FragmentActivity? = findActivity() as? FragmentActivity

fun Context.findAppCompatActivity(): AppCompatActivity? = findActivity() as? AppCompatActivity

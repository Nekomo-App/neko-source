package com.lagradost.shiro.utils

import android.app.Activity
import android.app.Dialog
import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

/*
 * Drop-in replacement for kotlinx.android.synthetic view lookups.
 * Resolves a view by id on the receiver, throwing on missing views like synthetics did.
 */
@Suppress("UNCHECKED_CAST")
fun <T : View> View.fv(@IdRes id: Int): T = findViewById(id) as T

@Suppress("UNCHECKED_CAST")
fun <T : View> Activity.fv(@IdRes id: Int): T = findViewById(id) as T

@Suppress("UNCHECKED_CAST")
fun <T : View> Fragment.fv(@IdRes id: Int): T = requireView().findViewById(id) as T

@Suppress("UNCHECKED_CAST")
fun <T : View> Dialog.fv(@IdRes id: Int): T = findViewById(id) as T

@Suppress("UNCHECKED_CAST")
fun <T : View> DialogFragment.fv(@IdRes id: Int): T = dialog!!.findViewById(id) as T

@Suppress("UNCHECKED_CAST")
fun <T : View> RecyclerView.ViewHolder.fv(@IdRes id: Int): T = itemView.findViewById(id) as T

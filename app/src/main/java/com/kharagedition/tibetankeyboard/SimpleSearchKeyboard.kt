package com.kharagedition.tibetankeyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class SimpleSearchKeyboard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onKeyClickListener: ((String) -> Unit)? = null
    private var onDeleteClickListener: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.simple_search_keyboard, this, true)
        setupKeyListeners()
    }

    private fun setupKeyListeners() {
        setKeyListener(R.id.key_q, "q")
        setKeyListener(R.id.key_w, "w")
        setKeyListener(R.id.key_e, "e")
        setKeyListener(R.id.key_r, "r")
        setKeyListener(R.id.key_t, "t")
        setKeyListener(R.id.key_y, "y")
        setKeyListener(R.id.key_u, "u")
        setKeyListener(R.id.key_i, "i")
        setKeyListener(R.id.key_o, "o")
        setKeyListener(R.id.key_p, "p")

        // Second row
        setKeyListener(R.id.key_a, "a")
        setKeyListener(R.id.key_s, "s")
        setKeyListener(R.id.key_d, "d")
        setKeyListener(R.id.key_f, "f")
        setKeyListener(R.id.key_g, "g")
        setKeyListener(R.id.key_h, "h")
        setKeyListener(R.id.key_j, "j")
        setKeyListener(R.id.key_k, "k")
        setKeyListener(R.id.key_l, "l")

        // Third row
        setKeyListener(R.id.key_z, "z")
        setKeyListener(R.id.key_x, "x")
        setKeyListener(R.id.key_c, "c")
        setKeyListener(R.id.key_v, "v")
        setKeyListener(R.id.key_b, "b")
        setKeyListener(R.id.key_n, "n")
        setKeyListener(R.id.key_m, "m")
        setKeyListener(R.id.key_space, " ")

        // Delete key
        findViewById<ImageView>(R.id.key_delete).setOnClickListener {
            onDeleteClickListener?.invoke()
        }
    }

    private fun setKeyListener(viewId: Int, key: String) {
        findViewById<TextView>(viewId).setOnClickListener {
            onKeyClickListener?.invoke(key)
        }
    }

    fun setOnKeyClickListener(listener: (String) -> Unit) {
        onKeyClickListener = listener
    }

    fun setOnDeleteClickListener(listener: () -> Unit) {
        onDeleteClickListener = listener
    }
}
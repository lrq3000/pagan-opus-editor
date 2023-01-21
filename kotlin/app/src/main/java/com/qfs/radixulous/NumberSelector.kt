package com.qfs.radixulous

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity.CENTER
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.internal.ViewUtils.dpToPx
import java.lang.Integer.min


class NumberSelector: LinearLayout {
    var min: Int = 0
    var max: Int = 1
    var button_map = HashMap<View, Int>()
    var active_button: View? = null
    var on_change_hook: ((NumberSelector) -> Unit)? = null

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.NumberSelector, 0, 0).apply {
            try {
                max = getInteger(R.styleable.NumberSelector_max, 2)
                min = getInteger(R.styleable.NumberSelector_min, 0)
            } finally {
                recycle()
            }
        }
        this.populate()
    }

    override fun onLayout(isChanged: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var current_state = this.getState()
        this.clear()
        this.populate()
        if (current_state != null) {
            this.setState(current_state)
        }
        super.onLayout(isChanged, left, top, right, bottom)
        var size = 1 + (this.max - this.min)
        var margin = 0
        var working_width = (this.width - (this.paddingLeft + this.paddingRight))
        var inner_width = (working_width - ((size - 1) * margin)) / size
        var remainder = working_width % inner_width
        for (i in this.min .. this.max) {
            var j = i - this.min
            var button = this.getChildAt(j)
            var x = (j * (margin + inner_width)) + this.paddingLeft
            var working_width = inner_width
            if (j < remainder) {
                working_width += 1
            }

            x += min(remainder, j)
            (button as TextView).gravity = CENTER
            button.layout(x, this.paddingTop, x + working_width, (bottom - top) - this.paddingBottom)
        }
    }

    fun getState(): Int? {
        if (this.active_button == null) {
            return null
        }
        return this.button_map[this.active_button] ?: null
    }

    fun setState(new_state: Int) {
        if (new_state < this.min || new_state > this.max) {
            throw Exception("OutOfBounds")
        }

        for ((button, value) in this.button_map) {
            if (value == new_state) {
                this.set_active_button(button)
                return
            }
        }
    }

    fun set_max(new_max: Int) {
        this.clear()
        this.max = new_max
        this.populate()
    }

    fun set_min(new_min: Int) {
        this.clear()
        this.min = new_min
        this.populate()
    }

    fun setRange(new_min: Int, new_max: Int) {
        var original_value = this.button_map[this.active_button]

        this.clear()
        this.min = new_min
        this.max = new_max
        this.populate()

        if (original_value != null) {
            if (original_value >= this.min && original_value <= this.max) {
                this.setState(original_value)
            } else if (original_value < this.min) {
                this.setState(this.min)
            } else {
                this.setState(this.max)
            }
        }
    }

    fun clear() {
        this.active_button = null
        this.button_map.clear()
        this.removeAllViews()
    }

    fun populate() {
        for (i in this.min .. this.max) {
            val currentView = TextView(this.context)
            this.addView(currentView)

            currentView.text = "${get_number_string(i, 12,2)}"
            this.button_map[currentView] = i
            currentView.background = when (i) {
                this.min -> {
                    resources.getDrawable(R.drawable.ns_start)
                }
                this.max -> {
                    resources.getDrawable(R.drawable.ns_end)
                }
                else -> {
                    resources.getDrawable(R.drawable.ns_middle)
                }
            }
            currentView.setOnTouchListener { view: View, motionEvent: MotionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_UP) {
                    this.set_active_button(view)
                    if (this.on_change_hook != null) {
                        this.on_change_hook!!(this)
                    }
                }
                false
            }
        }
    }
    fun setOnChange(hook: (NumberSelector) -> Unit) {
        this.on_change_hook = hook
    }

    fun set_active_button(view: View) {
        this.unset_active_button()
        this.active_button = view

        this.active_button!!.background = resources.getDrawable(
            when (this.getState()) {
                this.min -> {
                    R.drawable.ns_selected_start
                }
                this.max -> {
                    R.drawable.ns_selected_end
                }
                else -> {
                    R.drawable.ns_selected_middle
                }
            }
        )
    }

    fun unset_active_button() {
        if (this.active_button == null) {
            return
        }

        this.active_button!!.background = resources.getDrawable(
            when (this.getState()) {
                this.min -> {
                    R.drawable.ns_start
                }
                this.max -> {
                    R.drawable.ns_end
                }
                else -> {
                    R.drawable.ns_middle
                }
            }
        )

        this.active_button = null
    }
}
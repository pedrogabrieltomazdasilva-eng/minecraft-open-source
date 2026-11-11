package org.openmc.server

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

internal object UiKit {
    const val background = 0xFF090D0B.toInt()
    const val surface = 0xFF151C18.toInt()
    const val elevated = 0xFF1D2821.toInt()
    const val emerald = 0xFF3DE59B.toInt()
    const val white = 0xFFF2F8F3.toInt()
    const val muted = 0xFF9DACA1.toInt()
    const val border = 0xFF304037.toInt()
    const val warning = 0xFFFFC777.toInt()

    fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(context: Context, color: Int, radius: Int, outline: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = context.dp(radius).toFloat()
            if (outline != null) setStroke(context.dp(1), outline)
        }

    fun column(context: Context, padding: Int = 0) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        if (padding > 0) setPadding(context.dp(padding), context.dp(padding), context.dp(padding), context.dp(padding))
    }

    fun row(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    fun card(context: Context, padding: Int = 18) = column(context, padding).apply {
        background = shape(context, surface, 22, border)
    }

    fun navigationBackground(context: Context) = shape(context, surface, 24, border)

    fun text(context: Context, value: String, size: Float = 14f, color: Int = white, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setLineSpacing(context.dp(2).toFloat(), 1f)
        }

    fun title(context: Context, value: String) = text(context, value, 28f, white, true)

    fun button(context: Context, value: String, primary: Boolean = false, icon: Int? = null, onClick: (() -> Unit)? = null) =
        TextView(context).apply {
            text = value
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (primary) UiKit.background else white)
            gravity = Gravity.CENTER
            minHeight = context.dp(48)
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
            if (icon != null) {
                val drawable = context.getDrawable(icon)?.mutate()
                drawable?.setTint(if (primary) UiKit.background else UiKit.white)
                setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
                compoundDrawablePadding = context.dp(8)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(if (primary) 0x44000000 else 0x44FFFFFF),
                shape(context, if (primary) emerald else elevated, 14, if (primary) null else border), null
            )
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }

    fun field(context: Context, hintText: String, initial: String = "") = EditText(context).apply {
        hint = hintText
        setText(initial)
        setTextColor(white)
        setHintTextColor(muted)
        textSize = 15f
        setSingleLine(true)
        setPadding(context.dp(14), context.dp(10), context.dp(14), context.dp(10))
        background = shape(context, elevated, 13, border)
    }

    fun LinearLayout.add(view: View, top: Int = 0, bottom: Int = 0) {
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.topMargin = context.dp(top)
        params.bottomMargin = context.dp(bottom)
        addView(view, params)
    }

    fun LinearLayout.gap(size: Int) = addView(View(context), LinearLayout.LayoutParams(1, context.dp(size)))
}

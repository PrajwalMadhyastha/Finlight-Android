package io.pm.finlight.ui.components

import android.content.Context
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.vectorResource
import io.pm.finlight.R
import io.pm.finlight.utils.BankLogoHelper

private sealed interface SafeDrawableResource {
    data class Vector(val vector: ImageVector) : SafeDrawableResource

    data class Bitmap(val bitmap: ImageBitmap) : SafeDrawableResource
}

/**
 * Safely loads and remembers a [Painter] for an account's bank logo.
 * If the drawable fails to inflate or parse (e.g., AAPT truncation or OEM vector parsing bugs),
 * it gracefully falls back to [fallbackResId] (default: [R.drawable.ic_default_bank_logo])
 * instead of crashing the calling screen.
 *
 * @param accountName The account name (e.g., "Bank of India", "HDFC Bank").
 * @param fallbackResId The fallback drawable resource if inflation fails.
 */
@Composable
fun rememberSafeBankLogoPainter(
    accountName: String?,
    @DrawableRes fallbackResId: Int = R.drawable.ic_default_bank_logo,
): Painter {
    val logoResId =
        remember(accountName) {
            BankLogoHelper.getLogoForAccount(accountName)
        }
    return rememberSafeDrawablePainter(
        resId = logoResId,
        fallbackResId = fallbackResId,
    )
}

/**
 * Safely loads and remembers a [Painter] for a given drawable resource ID with fallback.
 *
 * @param logoResId The drawable resource ID to inflate.
 * @param fallbackResId The fallback drawable resource if inflation fails.
 */
@Composable
fun rememberSafeBankLogoPainter(
    @DrawableRes logoResId: Int,
    @DrawableRes fallbackResId: Int = R.drawable.ic_default_bank_logo,
): Painter =
    rememberSafeDrawablePainter(
        resId = logoResId,
        fallbackResId = fallbackResId,
    )

/**
 * Safely loads and remembers a [Painter] for any given drawable resource ID with optional fallback.
 * If the resource fails to inflate or parse, it gracefully falls back to [fallbackResId].
 *
 * @param resId The drawable resource ID to inflate.
 * @param fallbackResId The fallback drawable resource if inflation fails (0 for transparent placeholder).
 */
@Composable
fun rememberSafeDrawablePainter(
    @DrawableRes resId: Int,
    @DrawableRes fallbackResId: Int = 0,
): Painter {
    val context = LocalContext.current
    val resource =
        remember(resId, fallbackResId, context.theme) {
            safeLoadResource(context, resId)
                ?: if (fallbackResId != 0) safeLoadResource(context, fallbackResId) else null
        }

    return when (resource) {
        is SafeDrawableResource.Vector -> rememberVectorPainter(image = resource.vector)
        is SafeDrawableResource.Bitmap -> remember(resource.bitmap) { BitmapPainter(resource.bitmap) }
        null -> remember { ColorPainter(Color.Transparent) }
    }
}

/**
 * Defensive Composable image renderer for bank logos.
 */
@Composable
fun BankLogoImage(
    accountName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    @DrawableRes fallbackResId: Int = R.drawable.ic_default_bank_logo,
) {
    Image(
        painter = rememberSafeBankLogoPainter(accountName = accountName, fallbackResId = fallbackResId),
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
    )
}

private fun safeLoadResource(
    context: Context,
    @DrawableRes resId: Int,
): SafeDrawableResource? {
    if (resId == 0) return null
    val res = context.resources
    val value = TypedValue()
    try {
        res.getValue(resId, value, true)
    } catch (_: Throwable) {
        return null
    }

    val path = value.string ?: return null
    return if (path.endsWith(".xml")) {
        try {
            val vector =
                ImageVector.vectorResource(
                    theme = context.theme,
                    res = res,
                    resId = resId,
                )
            SafeDrawableResource.Vector(vector)
        } catch (_: Throwable) {
            null
        }
    } else {
        try {
            val bitmap = ImageBitmap.imageResource(res, resId)
            SafeDrawableResource.Bitmap(bitmap)
        } catch (_: Throwable) {
            null
        }
    }
}

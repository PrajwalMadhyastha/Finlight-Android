// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/ui/components/InlineTextToolbar.kt
// REASON: FIX (Copy/Paste in ModalBottomSheet) - Extracted the InlineTextToolbar
// workaround into a shared component. The system PopupWindow-based text selection
// toolbar cannot receive touch events inside a ModalBottomSheet due to a Compose
// AnchoredDraggable conflict (issuetracker.google.com/307677220). This inline
// toolbar renders Cut/Copy/Paste as regular Compose buttons, bypassing the bug.
// =================================================================================
package io.pm.finlight.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.dp

/**
 * A [TextToolbar] implementation that exposes Cut/Copy/Paste as Compose state
 * instead of a system [android.widget.PopupWindow].
 *
 * The system toolbar cannot receive touch events inside a [androidx.compose.material3.ModalBottomSheet]
 * because the sheet's AnchoredDraggable modifier intercepts them. This class
 * sidesteps the issue entirely by keeping the actions as observable state that
 * the UI can render as normal Composables.
 *
 * Use via [rememberInlineTextToolbar] and [InlineTextToolbarActionBar].
 */
class InlineTextToolbar : TextToolbar {

    var showCut by mutableStateOf(false)
        private set
    var showCopy by mutableStateOf(false)
        private set
    var showPaste by mutableStateOf(false)
        private set

    private var onCutCallback: (() -> Unit)? = null
    private var onCopyCallback: (() -> Unit)? = null
    private var onPasteCallback: (() -> Unit)? = null

    override val status: TextToolbarStatus
        get() = if (showCut || showCopy || showPaste) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        showCut = onCutRequested != null
        showCopy = onCopyRequested != null
        showPaste = onPasteRequested != null
        onCutCallback = onCutRequested
        onCopyCallback = onCopyRequested
        onPasteCallback = onPasteRequested
    }

    override fun hide() {
        showCut = false
        showCopy = false
        showPaste = false
        onCutCallback = null
        onCopyCallback = null
        onPasteCallback = null
    }

    fun cut() = onCutCallback?.invoke()
    fun copy() = onCopyCallback?.invoke()
    fun paste() = onPasteCallback?.invoke()
}

/** Creates and remembers an [InlineTextToolbar] for the current composition. */
@Composable
fun rememberInlineTextToolbar(): InlineTextToolbar = remember { InlineTextToolbar() }

/**
 * Renders a compact Cut/Copy/Paste action bar that is animated in when the
 * [toolbar]'s status is [TextToolbarStatus.Shown] (i.e. text is selected).
 *
 * Place this directly above the [OutlinedTextField] / [BasicTextField] that
 * uses the toolbar via [LocalTextToolbar].
 */
@Composable
fun InlineTextToolbarActionBar(toolbar: InlineTextToolbar) {
    AnimatedVisibility(
        visible = toolbar.status == TextToolbarStatus.Shown,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            if (toolbar.showCut) {
                TextButton(onClick = { toolbar.cut() }) {
                    Icon(
                        Icons.Default.ContentCut,
                        contentDescription = "Cut",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Cut", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (toolbar.showCopy) {
                TextButton(onClick = { toolbar.copy() }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (toolbar.showPaste) {
                TextButton(onClick = { toolbar.paste() }) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Paste", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

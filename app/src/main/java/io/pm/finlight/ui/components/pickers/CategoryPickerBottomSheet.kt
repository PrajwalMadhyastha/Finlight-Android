package io.pm.finlight.ui.components.pickers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.pm.finlight.Category
import io.pm.finlight.ui.components.CategorySelectionGrid

@Composable
fun CategoryPickerBottomSheet(
    categories: List<Category>,
    selectedCategoryId: Int? = null,
    onCategorySelected: (Category) -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    title: String = "Select Category",
    onAddNew: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .navigationBarsPadding()
                .fillMaxHeight(),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        CategorySelectionGrid(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = onCategorySelected,
            onAddNew = onAddNew,
        )
        Spacer(Modifier.height(16.dp))
    }
}

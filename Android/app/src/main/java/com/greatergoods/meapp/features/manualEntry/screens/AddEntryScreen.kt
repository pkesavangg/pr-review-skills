import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.manualEntry.viewmodel.EntryViewModel

@Composable
fun AddEntryScreen(
    viewModel: EntryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
) {
}

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.presentation.viewmodel.EntryViewModel

@Composable
fun AddEntryScreen(
    viewModel: EntryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
}

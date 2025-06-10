import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.ui.shared.components.AppButton
import com.greatergoods.meapp.ui.shared.components.ButtonSize
import com.greatergoods.meapp.ui.shared.components.ButtonType

@Composable
fun ButtonVariants(){
    AppButton(
        type = ButtonType.PrimaryFilled,
        text = "PrimaryFilled",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here
    AppButton(
        type = ButtonType.PrimaryFilled,
        text = "PrimaryFilled",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = false
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.PrimaryOutlined,
        text = "PrimaryOutlined",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.SecondaryFilled,
        text = "SecondaryFilled",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.SecondaryOutlined,
        text = "SecondaryOutlined",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.TextPrimary,
        text = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.TextSecondary,
        text = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here
    AppButton(
        type = ButtonType.TextSecondary,
        text = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = false
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.InlineText,
        text = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )

    Spacer(modifier = Modifier.height(16.dp))

    AppButton(
        type = ButtonType.InlineText,
        text = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = false
    )

    Spacer(modifier = Modifier.height(16.dp))


}

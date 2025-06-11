import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.components.AppButton
import com.greatergoods.meapp.features.common.components.ButtonSize
import com.greatergoods.meapp.features.common.components.ButtonType

@Composable
fun ButtonVariants(){
    AppButton(
        type = ButtonType.PrimaryFilled,
        label = "PrimaryFilled",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here
    AppButton(
        type = ButtonType.PrimaryFilled,
        label = "PrimaryFilled",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = false
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.PrimaryOutlined,
        label = "PrimaryOutlined",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.SecondaryFilled,
        label = "SecondaryFilled",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.SecondaryOutlined,
        label = "SecondaryOutlined",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.TextPrimary,
        label = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.TextSecondary,
        label = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here
    AppButton(
        type = ButtonType.TextSecondary,
        label = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = false
    )
    Spacer(modifier = Modifier.height(16.dp)) // Added space here

    AppButton(
        type = ButtonType.InlineText,
        label = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = true
    )

    Spacer(modifier = Modifier.height(16.dp))

    AppButton(
        type = ButtonType.InlineText,
        label = "PrimaryButton",
        onClick = {},
        modifier = Modifier.width(300.dp).height(48.dp),
        size = ButtonSize.Medium,
        enabled = false
    )

    Spacer(modifier = Modifier.height(16.dp))


}

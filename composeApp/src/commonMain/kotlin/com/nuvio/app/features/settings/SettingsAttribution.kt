package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.build.AppVersionPolicy
import com.nuvio.app.core.build.NuvioDeckVersionConfig
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_about_version_format
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsAttribution(
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (isTablet) 20.dp else 16.dp),
    ) {
        MemberBrandWordmark(
            height = if (isTablet) 30.dp else 26.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(
            modifier = Modifier.height(if (isTablet) 10.dp else 8.dp),
        )

        Text(
            text = NuvioDeckVersionConfig.FORK_ATTRIBUTION_TEXT,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(
            modifier = Modifier.height(4.dp),
        )
        Text(
            text = stringResource(
                Res.string.compose_about_version_format,
                NuvioDeckVersionConfig.FORK_VERSION_NAME,
                AppVersionPolicy.displayVersionCode,
            ),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

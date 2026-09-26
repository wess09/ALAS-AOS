package com.azurpilot.ghio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.azurpilot.ghio.R
import com.azurpilot.ghio.update.ReleaseUrls

/**
 * 下载源选择器：直连 + 内置镜像 + 自定义，设置页与首启部署页共用同一组选项与存储值。
 * 自定义前缀的输入框只在设置页提供，部署页选了自定义但还没填时按直连处理。
 */
@Composable
fun MirrorSourcePicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    arrangement: Alignment.Horizontal = Alignment.Start,
) {
    AppSingleChoiceFlow(
        options = buildList {
            add(ReleaseUrls.DIRECT to stringResource(R.string.provision_source_direct))
            ReleaseUrls.MIRRORS.forEach { add(it to ReleaseUrls.displayLabel(it)) }
            add(ReleaseUrls.CUSTOM to stringResource(R.string.provision_source_custom))
        },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        arrangement = arrangement,
    )
}

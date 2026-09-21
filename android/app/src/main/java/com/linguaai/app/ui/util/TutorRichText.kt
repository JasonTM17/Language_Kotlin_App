package com.linguaai.app.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lays out parsed [TutorBlock]s as a tutor reply. */
@Composable
fun TutorRichText(
    content: String,
    modifier: Modifier = Modifier,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
) {
    val blocks = remember(content) { parseTutorMarkdown(content) }
    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is TutorBlock.Line ->
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = overflow,
                        maxLines = maxLines,
                    )

                is TutorBlock.Bullet ->
                    Row(modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            overflow = overflow,
                            maxLines = maxLines,
                        )
                    }

                is TutorBlock.Code ->
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(CODE_BLOCK_RADIUS_DP.dp),
                                ).padding(10.dp),
                    )
            }
        }
    }
}

private const val CODE_BLOCK_RADIUS_DP = 10

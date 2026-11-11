package app.nightrecord.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import app.nightrecord.ui.components.WinRateRing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nightrecord.data.SearchHit
import app.nightrecord.ui.components.EmptyState
import app.nightrecord.ui.components.NightPanel
import app.nightrecord.ui.components.SectionHeader
import app.nightrecord.ui.components.pct
import app.nightrecord.ui.theme.NightBg
import app.nightrecord.ui.theme.NightGold
import app.nightrecord.ui.theme.NightLine
import app.nightrecord.ui.theme.NightMuted
import app.nightrecord.ui.theme.NightPanel
import app.nightrecord.ui.theme.NightText

@Composable
fun SearchScreen(
    query: String,
    suggest: List<SearchHit>,
    results: List<SearchHit>,
    busy: Boolean,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
    ) {
        Text("밤의 기록", color = NightGold, fontSize = 11.sp, letterSpacing = 1.4.sp)
        Text(
            "플레이어 검색",
            color = NightText,
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text("공개된 전적을 태그·이름으로 찾습니다.", color = NightMuted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))

        val colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NightGold,
            unfocusedBorderColor = NightLine,
            focusedTextColor = NightText,
            unfocusedTextColor = NightText,
            cursorColor = NightGold,
            focusedContainerColor = NightPanel,
            unfocusedContainerColor = NightPanel,
            focusedLabelColor = NightGold,
            unfocusedLabelColor = NightMuted,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("이름 또는 태그") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = colors,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onSearch,
            enabled = query.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NightGold, contentColor = NightBg),
        ) {
            Text("검색", modifier = Modifier.padding(vertical = 6.dp))
        }

        if (busy) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = NightGold, modifier = Modifier.align(Alignment.CenterHorizontally))
            return
        }

        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (suggest.isNotEmpty()) {
                item { SectionHeader("자동완성") }
                items(suggest) { hit -> SearchRow(hit, onOpen) }
            }
            item { SectionHeader("검색 결과") }
            if (results.isEmpty()) {
                item { EmptyState("검색어를 입력한 뒤 결과를 확인하세요.") }
            } else {
                items(results) { hit -> SearchRow(hit, onOpen) }
            }
        }
    }
}

@Composable
private fun SearchRow(hit: SearchHit, onOpen: (String) -> Unit) {
    val rate = if (hit.matches == 0) 0.0 else hit.wins.toDouble() / hit.matches
    NightPanel(modifier = Modifier.clickable { onOpen(hit.displayTag.ifBlank { hit.tag }) }) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    hit.displayTag.ifBlank { hit.tag },
                    color = NightText,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hit.names.isNotEmpty()) {
                    Text(
                        hit.names.take(3).joinToString(" · "),
                        color = NightMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${hit.matches}전 ${hit.wins}승" + if (hit.peakRating > 0) " · 심도 최고 ${hit.peakRating}" else "",
                    color = NightMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(10.dp))
            WinRateRing(rate, diameter = 48.dp)
        }
    }
}

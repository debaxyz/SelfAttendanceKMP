@file:OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class
)

package com.debaxyz.selfattendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.debaxyz.selfattendance.platform.FileActions
import com.debaxyz.selfattendance.data.model.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.math.max

/* ============================================================
 *  SHARED COMPONENTS & COLOR ROLES
 * ============================================================ */

@Immutable
data class CC(val bg: Color, val fg: Color)

@Composable
fun cc(kind: String): CC {
    val c = MaterialTheme.colorScheme
    return when (kind) {
        "d" -> CC(c.tertiaryContainer, c.onTertiaryContainer)
        "n" -> CC(c.secondaryContainer, c.onSecondaryContainer)
        "v" -> CC(c.errorContainer, c.onErrorContainer)
        "w" -> CC(c.primaryContainer, c.onPrimaryContainer)
        "f" -> CC(c.primary, c.onPrimary)
        else -> CC(c.surfaceContainerHighest, c.onSurface)
    }
}

fun kindOf(r: Rec): String = when (r.s) {
    "v" -> "v"
    "w" -> "w"
    "f" -> "f"
    else -> if (r.sh == "n") "n" else "d"
}

fun kindIcon(kind: String): ImageVector = when (kind) {
    "v" -> Icons.Rounded.EventBusy
    "w" -> Icons.Rounded.EventAvailable
    "f" -> Icons.Rounded.Celebration
    "n" -> Icons.Rounded.NightsStay
    else -> Icons.Rounded.WbSunny
}

@Composable
fun SectionTitle(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Tag(text: String, kind: String) {
    val c = cc(kind)
    Surface(color = c.bg, contentColor = c.fg, shape = CircleShape) {
        Text(
            text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, maxLines = 1
        )
    }
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) { Column(Modifier.padding(20.dp), content = content) }
}

@Composable
fun DownloadButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = Modifier.testTag("download_button_$label")
    ) {
        Icon(Icons.Rounded.Download, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1)
    }
}

data class Mini(val big: String, val small: String, val kind: String? = null)

@Composable
fun MiniGrid(items: List<Mini>, cols: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    val c = m.kind?.let { cc(it) }
                    Surface(
                        Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        color = c?.bg ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = c?.fg ?: MaterialTheme.colorScheme.onSurface
                    ) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(m.big, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.small, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* ============================================================
 *  HOME VIEW
 * ============================================================ */

@Composable
fun HomeView(vm: AttendanceViewModel, files: FileActions) {
    val cs = MaterialTheme.colorScheme
    val cfg = vm.cfg
    val c = cfg.currency
    val ym = vm.viewMonth
    val st = remember(vm.data, vm.cfg, ym) { monthStats(ym, vm.data, vm.cfg) }
    val monthName = st.name

    // Clock Card
    val t = vm.now
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer)
    ) {
        Text(
            "${pad2(t.hour)}:${pad2(t.minute)}:${pad2(t.second)}",
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
    }

    // Estimated Earnings Card
    SectionCard {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle("Estimated Earnings", "$monthName total pay", Modifier.weight(1f))
            AssistChip(
                onClick = { vm.tab = "settings" },
                label = { Text("Set rates") },
                leadingIcon = { Icon(Icons.Rounded.Settings, null, Modifier.size(AssistChipDefaults.IconSize)) }
            )
        }
        Surface(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer
        ) {
            Column(Modifier.padding(18.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total salary for $monthName", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                Text("$c${money(st.totalMoney)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    "Base ($c${money(st.baseMoney)}) + OT ($c${money(st.otMoney)}) + Festival ($c${money(st.festivalMoney)})",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
                )
            }
        }
        MiniGrid(
            listOf(
                Mini("$c${num(cfg.dayRate)}", "1 day shift money"),
                Mini("$c${num(cfg.nightRate)}", "1 night shift money"),
                Mini("$c${num(cfg.otHourRate)}/h", "1h OT money"),
                Mini("$c${money(st.baseMoney)}", "Base (${st.dayShifts}D + ${st.nightShifts}N)"),
                Mini("$c${money(st.otMoney)}", "OT (${f1(st.overtime)}h)"),
                Mini("$c${money(st.festivalMoney)}", "Festival (${st.festivalDays}d)", "f")
            ), 3
        )
    }

    // Month Stats Grid
    val stats = listOf(
        "${st.present}" to "Present", "${st.late}" to "Late", "${st.leave}" to "Leave", "${st.hours}" to "Hours",
        "${st.dayShifts}" to "Day shifts", "${st.nightShifts}" to "Night shifts", "${st.weeklyOff}" to "Weekly off", f1(st.overtime) to "Overtime (h)"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (b, s) ->
                    Card(
                        Modifier.weight(1f), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(b, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(s, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }
        }
    }

    // Calendar Card
    SectionCard {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { vm.viewMonth = ym.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
            Text(monthName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FilledTonalIconButton(onClick = { vm.viewMonth = ym.plusMonths(1) }) { Icon(Icons.Rounded.ChevronRight, "Next month") }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DOW.forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        val first = ym.atDay(1).dayOfWeek.isoDayNumber % 7
        val cells: List<Int?> = List(first) { null } + (1..ym.lengthOfMonth()).toList()
        val todayKey = vm.today.toString()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { d ->
                        if (d == null) Spacer(Modifier.weight(1f))
                        else {
                            val k = ym.atDay(d).toString()
                            DayCell(d, vm.data[k], k == todayKey, Modifier.weight(1f)) { vm.editingKey = k }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendItem(cc("d").bg, "Day")
            LegendItem(cc("n").bg, "Night")
            LegendItem(cs.error, "Late", circle = true)
            LegendItem(cc("v").bg, "Leave")
            LegendItem(cc("w").bg, "Weekly off")
            LegendItem(cc("f").bg, "Festival")
        }
        Text(
            "Tap any date to record shift, overtime hours, or leave.",
            Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }

    // Month Report Card
    SectionCard {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Month Total Report", "$monthName summary", Modifier.weight(1f))
            DownloadButton("CSV") { files.saveCsv(vm.monthCsv(ym)) }
        }
        MiniGrid(
            listOf(
                Mini("${st.present}", "Total present"), Mini("${st.dayShifts}", "Day"),
                Mini("${st.nightShifts}", "Night"), Mini("${f1(st.overtime)}h", "Overtime")
            ), 4
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Total work hours: ${st.hours} hrs", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.tab = "settings" }) { Text("All months") }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, circle: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.padding(end = 6.dp).size(if (circle) 8.dp else 12.dp)
                .background(color, if (circle) CircleShape else MaterialTheme.shapes.extraSmall)
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DayCell(d: Int, rec: Rec?, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val c = rec?.let { cc(kindOf(it)) }
    Box(
        modifier.aspectRatio(1f).clip(shape)
            .background(c?.bg ?: Color.Transparent)
            .then(if (isToday) Modifier.border(2.dp, cs.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .testTag("day_cell_$d"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$d", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            color = c?.fg ?: cs.onSurface
        )
        if (rec?.s == "l") Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).background(cs.error, CircleShape))
        if (rec?.s == "f") Icon(
            Icons.Rounded.Celebration, null,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(11.dp), tint = c!!.fg
        )
    }
}

/* ============================================================
 *  RECENT VIEW
 * ============================================================ */

@Composable
fun RecentView(vm: AttendanceViewModel, files: FileActions) {
    val cs = MaterialTheme.colorScheme
    val cfg = vm.cfg
    val c = cfg.currency
    val all = vm.data.keys.sortedDescending()
    val f = vm.filter
    val list = all.filter { k ->
        val r = vm.data.getValue(k)
        val work = r.s != "v" && r.s != "w" && r.s != "f"
        when (f) {
            "d" -> work && r.sh != "n"
            "n" -> work && r.sh == "n"
            "f" -> r.s == "f"
            "v" -> r.s == "v"
            "w" -> r.s == "w"
            "ot" -> (r.ot ?: 0.0) > 0
            else -> true
        }
    }
    val filters = listOf(
        Triple("all", "All", Icons.Rounded.History),
        Triple("d", "Day", Icons.Rounded.WbSunny),
        Triple("n", "Night", Icons.Rounded.NightsStay),
        Triple("f", "Festival", Icons.Rounded.Celebration),
        Triple("ot", "Overtime", Icons.Rounded.Schedule),
        Triple("v", "Leave", Icons.Rounded.EventBusy),
        Triple("w", "Weekly off", Icons.Rounded.EventAvailable)
    )

    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Recent Days History", "${all.size} total logged record(s)", Modifier.weight(1f))
            if (all.isNotEmpty()) DownloadButton("CSV") { files.saveCsv(vm.monthCsv(vm.viewMonth)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { (k, l, icon) ->
                FilterChip(
                    selected = f == k, onClick = { vm.filter = k },
                    label = { Text(l) },
                    leadingIcon = { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) }
                )
            }
        }
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty()) {
                Text(
                    "No records match your filter. Tap dates on the calendar in Home to add attendance records.",
                    Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center
                )
            } else list.forEach { k ->
                val e = vm.data.getValue(k)
                val label = fmtDate(LocalDate.parse(k), "EEE, d MMM yyyy")
                val name = if (e.sh == "n") "Night" else "Day"
                val otS = if ((e.ot ?: 0.0) > 0) " · OT ${num(e.ot!!)}h" else ""
                val detail = when {
                    e.s == "v" -> "Leave"
                    e.s == "w" -> "Weekly off"
                    e.s == "f" -> "Festival day · 1 day festival money ($c${num(cfg.festivalRate)})$otS"
                    e.inT == null -> "$name shift · ${num(round1(workedHours(e, cfg.std)))}h worked$otS"
                    else -> "$name · ${fmtTime(e.inT)} – ${e.outT?.let { fmtTime(it) } ?: "still working"}$otS"
                }
                val kind = kindOf(e)
                val tag = when (e.s) {
                    "f" -> "Festival"; "p" -> "On time"; "l" -> "Late"; "w" -> "WO"; "v" -> "Leave"
                    else -> name
                }
                val tagKind = when (e.s) { "p" -> "x"; "l" -> "d"; else -> kind }
                val cl = cc(kind)
                ListItem(
                    modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { vm.editingKey = k },
                    colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh),
                    leadingContent = {
                        Surface(shape = CircleShape, color = cl.bg, contentColor = cl.fg, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(kindIcon(kind), null, Modifier.size(22.dp)) }
                        }
                    },
                    headlineContent = { Text(label, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(detail) },
                    trailingContent = { Tag(tag, tagKind) }
                )
            }
        }
    }
}

/* ============================================================
 *  SETTINGS VIEW
 * ============================================================ */

@Composable
fun RateField(
    label: String, sub: String, value: Double, currency: String, suffix: String?,
    icon: ImageVector?, chips: List<Pair<String, () -> Unit>>, onValue: (Double) -> Unit
) {
    var text by remember { mutableStateOf(num(value)) }
    LaunchedEffect(value) { if ((text.toDoubleOrNull() ?: 0.0) != value) text = num(value) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { t -> text = t; onValue(max(0.0, t.toDoubleOrNull() ?: 0.0)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            prefix = { Text(currency) },
            suffix = suffix?.let { s -> { Text(s) } },
            leadingIcon = icon?.let { i -> { Icon(i, null) } },
            supportingText = { Text(sub) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chips.forEach { (l, a) -> AssistChip(onClick = a, label = { Text(l) }) }
        }
    }
}

@Composable
fun SettingsView(vm: AttendanceViewModel, files: FileActions) {
    val cs = MaterialTheme.colorScheme
    val cfg = vm.cfg
    val c = cfg.currency
    val list = remember(vm.data, vm.cfg, vm.viewMonth, vm.today) { vm.allMonths() }
    val tot = totals(list)
    val auto = (cfg.dayRate / cfg.std).toLong().toDouble()

    // Account & Cloud Sync Card
    SectionCard {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle("Google Account & Cloud", "Sync status and user profile", Modifier.weight(1f))
            Icon(Icons.Rounded.CloudDone, null, tint = cs.primary)
        }
        val user = vm.currentUser
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = cs.primary,
                    contentColor = cs.onPrimary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val initial = (user?.displayName?.firstOrNull() ?: user?.email?.firstOrNull() ?: 'U').uppercaseChar()
                        Text(initial.toString(), fontWeight = FontWeight.Bold)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(user?.displayName ?: "Google User", fontWeight = FontWeight.Bold)
                    Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                OutlinedButton(
                    onClick = { vm.showProfileDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Manage")
                }
            }
        }
    }

    // Money Rates Card
    SectionCard {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Manual Set Money", "Set day shift, night shift and OT money rates", Modifier.weight(1f))
            Icon(Icons.Rounded.Payments, null, tint = cs.primary)
        }
        Text("Currency symbol", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        FlowRow(Modifier.padding(top = 6.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CURRENCIES.forEach { x ->
                FilterChip(selected = c == x, onClick = { vm.persistConfig(cfg.copy(currency = x)); vm.showToast("Currency set to $x") }, label = { Text(x) })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RateField(
                "Day shift money (1 day)", "Pay for one full day-shift day", cfg.dayRate, c, null, Icons.Rounded.LightMode,
                listOf(
                    "+50" to { vm.setRate("dayRate", cfg.dayRate + 50) },
                    "+100" to { vm.setRate("dayRate", cfg.dayRate + 100) },
                    "+500" to { vm.setRate("dayRate", cfg.dayRate + 500) }
                )
            ) { vm.setRate("dayRate", it) }
            RateField(
                "Night shift money (1 night)", "Pay for one full night-shift day", cfg.nightRate, c, null, Icons.Rounded.DarkMode,
                listOf(
                    "Same as day ($c${num(cfg.dayRate)})" to { vm.setRate("nightRate", cfg.dayRate) },
                    "+50" to { vm.setRate("nightRate", cfg.nightRate + 50) },
                    "+100" to { vm.setRate("nightRate", cfg.nightRate + 100) }
                )
            ) { vm.setRate("nightRate", it) }
            RateField(
                "One hour OT money (OT rate)", "Additional pay per overtime hour", cfg.otHourRate, c, "/h", Icons.Rounded.Schedule,
                listOf(
                    "+10" to { vm.setRate("otHourRate", cfg.otHourRate + 10) },
                    "+25" to { vm.setRate("otHourRate", cfg.otHourRate + 25) },
                    "Auto ($c${num(auto)}/h)" to { vm.setRate("otHourRate", auto) }
                )
            ) { vm.setRate("otHourRate", it) }
            RateField(
                "One day festival money", "Daily wage or bonus for festival days", cfg.festivalRate, c, null, Icons.Rounded.Celebration,
                listOf(
                    "Same as day shift ($c${num(cfg.dayRate)})" to { vm.setRate("festivalRate", cfg.dayRate) },
                    "+50" to { vm.setRate("festivalRate", cfg.festivalRate + 50) },
                    "+100" to { vm.setRate("festivalRate", cfg.festivalRate + 100) }
                )
            ) { vm.setRate("festivalRate", it) }
        }
        Surface(
            Modifier.fillMaxWidth().padding(top = 16.dp), shape = MaterialTheme.shapes.large,
            color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Live formula preview", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("20 day shifts × $c${num(cfg.dayRate)} = $c${money((20 * cfg.dayRate).toLong())}", style = MaterialTheme.typography.bodyMedium)
                Text("6 night shifts × $c${num(cfg.nightRate)} = $c${money((6 * cfg.nightRate).toLong())}", style = MaterialTheme.typography.bodyMedium)
                Text("15 hours OT × $c${num(cfg.otHourRate)} = $c${money((15 * cfg.otHourRate).toLong())}", style = MaterialTheme.typography.bodyMedium)
                Text("2 festival days × $c${num(cfg.festivalRate)} = $c${money((2 * cfg.festivalRate).toLong())}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Total salary = $c${money((20 * cfg.dayRate + 6 * cfg.nightRate + 15 * cfg.otHourRate + 2 * cfg.festivalRate).toLong())}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    // Month List Total Card
    SectionCard {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Month List Total", "All-time summary, money and download", Modifier.weight(1f))
        }
        DownloadButton("Download all total") { files.saveCsv(vm.allCsv()) }
        Box(Modifier.padding(vertical = 16.dp)) {
            MiniGrid(
                listOf(
                    Mini("${tot.present}", "All present"), Mini("${tot.festivalDays}", "Festival", "f"),
                    Mini("${tot.dayShifts}", "Day"), Mini("$c${money(tot.totalMoney)}", "Total salary")
                ), 4
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { m ->
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${m.present} present · ${m.dayShifts} day · ${m.nightShifts} night · ${m.hours} hours",
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                                )
                            }
                            FilledTonalIconButton(onClick = { files.saveCsv(vm.monthCsv(m.ym)) }) { Icon(Icons.Rounded.Download, "Download CSV for ${m.name}") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag("Salary: $c${money(m.totalMoney)}", "w")
                            Tag("OT: ${f1(m.overtime)}h ($c${money(m.otMoney)})", "d")
                            if (m.festivalDays > 0) Tag("${m.festivalDays} festival ($c${money(m.festivalMoney)})", "f")
                            Tag("${m.leave} leave", "v")
                            Tag("${m.weeklyOff} WO", "n")
                        }
                    }
                }
            }
        }
    }

    // Shift Settings Card
    SectionCard {
        Text("Shift Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ListItem(
            modifier = Modifier.padding(top = 8.dp).clip(MaterialTheme.shapes.large),
            colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh),
            headlineContent = { Text("Standard shift hours", fontWeight = FontWeight.Bold) },
            supportingContent = { Text("Base work hours per day (default 8h)") },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalIconButton(onClick = { vm.updateStd(-1) }) { Icon(Icons.Rounded.Remove, "Decrease") }
                    Text("${cfg.std}h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 32.dp), textAlign = TextAlign.Center)
                    FilledTonalIconButton(onClick = { vm.updateStd(1) }) { Icon(Icons.Rounded.Add, "Increase") }
                }
            }
        )
    }

    // Data & Backup Card
    SectionCard {
        Text("Data & Backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { files.saveJson(vm.backupJson()) }, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.ContentPadding) {
                Icon(Icons.Rounded.Backup, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Backup", maxLines = 1)
            }
            OutlinedButton(onClick = { files.restore() }, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.ContentPadding) {
                Icon(Icons.Rounded.Restore, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Restore", maxLines = 1)
            }
        }
        TextButton(
            onClick = { vm.showReset = true },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = cs.error)
        ) {
            Icon(Icons.Rounded.DeleteForever, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Clear all attendance data")
        }
        if (vm.currentUser != null) {
            TextButton(
                onClick = { vm.deleteAccountError = null; vm.showDeleteAccount = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = cs.error)
            ) {
                Icon(Icons.Rounded.PersonRemove, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Delete account & cloud data")
            }
        }
    }

    // About & open source licenses
    AboutCard()
}

/* ============================================================
 *  EDIT SHEET & RESET DIALOG
 * ============================================================ */

@Composable
fun StatusButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
    if (selected) Button(onClick, modifier, contentPadding = PaddingValues(horizontal = 8.dp), content = content)
    else FilledTonalButton(onClick, modifier, contentPadding = PaddingValues(horizontal = 8.dp), content = content)
}

@Composable
fun EditSheet(vm: AttendanceViewModel, key: String) {
    val r = vm.data[key]
    val work = r != null && r.s != "v" && r.s != "w"
    val title = fmtDate(LocalDate.parse(key), "EEEE, d MMMM yyyy")
    fun on(sh: String) = work && (r?.sh ?: "d") == sh

    ModalBottomSheet(
        onDismissRequest = { vm.editingKey = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Text("Shift", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = on("d"), onClick = { vm.chooseShift("d") },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Rounded.WbSunny, null, Modifier.size(SegmentedButtonDefaults.IconSize)) }
                ) { Text("Day") }
                SegmentedButton(
                    selected = on("n"), onClick = { vm.chooseShift("n") },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Rounded.NightsStay, null, Modifier.size(SegmentedButtonDefaults.IconSize)) }
                ) { Text("Night") }
            }

            Text("Overtime (OT hours)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OT_VALUES.forEach { v ->
                    val selected = work && (r?.ot ?: 0.0) == v
                    val label = when (v) {
                        0.0 -> "None"
                        else -> num(v)
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { vm.setOT(v) },
                        label = { Text(label) }
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusButton("Leave", Icons.Rounded.EventBusy, r?.s == "v", Modifier.weight(1f)) { vm.setEdit(Rec(date = key, s = "v")) }
                StatusButton("WO", Icons.Rounded.EventAvailable, r?.s == "w", Modifier.weight(1f)) { vm.setEdit(Rec(date = key, s = "w")) }
                StatusButton("Festival", Icons.Rounded.Celebration, r?.s == "f", Modifier.weight(1f)) { vm.setFestival() }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.clearDay() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Clear")
                }
                Button(onClick = { vm.editingKey = null }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun ResetDialog(vm: AttendanceViewModel) {
    AlertDialog(
        onDismissRequest = { vm.showReset = false },
        icon = { Icon(Icons.Rounded.DeleteForever, null) },
        title = { Text("Clear all attendance data?") },
        text = { Text("This will permanently delete all your check-in records, shift history, and overtime logs from this device and the cloud.") },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.showReset = false
                    vm.clearAllAttendance()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Yes, delete") }
        },
        dismissButton = { TextButton(onClick = { vm.showReset = false }) { Text("Cancel") } }
    )
}

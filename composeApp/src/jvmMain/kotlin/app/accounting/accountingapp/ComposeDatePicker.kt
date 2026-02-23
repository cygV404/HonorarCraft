package app.accounting.accountingapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@Composable
fun ComposeDatePicker(
    selectedDate: String,
    onDateSelected: (String) -> Unit
) {
    val displayFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    var showCalendar by remember { mutableStateOf(false) }

    var tempDate by remember(selectedDate) {
        mutableStateOf(
            if (selectedDate.isNotEmpty()) selectedDate
            else LocalDate.now().format(displayFormatter)
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        // Box für die Klick-Ebene
        Box(modifier = Modifier.fillMaxWidth(0.5f)) {
            OutlinedTextField(
                value = tempDate,
                onValueChange = {},
                label = { Text("Datum") },
                readOnly = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 16.sp,
                    //   fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Text("📅", modifier = Modifier.padding(end = 8.dp))
                }
            )

            // Unsichtbarer Layer für die Hand und den Klick
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showCalendar = !showCalendar
                    }
            )
        }

        if (showCalendar) {
            Popup(
                alignment = Alignment.TopStart,
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = { showCalendar = false }
            ) {
                CalendarView(
                    selectedDate = tempDate,
                    onDateSelected = { date ->
                        val formatted = date.format(displayFormatter)
                        tempDate = formatted
                        onDateSelected(formatted)
                        showCalendar = false
                    },
                    modifier = Modifier
                        .shadow(8.dp, RoundedCornerShape(8.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .background(Color(0xFFEDCDFD))
                        .width(300.dp)
                )
            }
        }
    }
}

@Composable
fun CalendarView(
    selectedDate: String,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // Format check Datum
    val parsedDate = remember(selectedDate) {
        try {
            if (selectedDate.isNotEmpty()) LocalDate.parse(selectedDate, formatter)
            else LocalDate.now()
        } catch (e: Exception) {
            LocalDate.now()
        }
    }

    var currentMonth by remember { mutableStateOf(parsedDate.withDayOfMonth(1)) }
    val today = LocalDate.now()

    Column(
        modifier = modifier
            .padding(8.dp)
    ) {
        // Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Text("<", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)} ${currentMonth.year}",
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Text(">", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Wochentage
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
            }
        }

        // Kalender-Gitter
        val firstDayOfMonth = currentMonth.dayOfWeek.value - 1
        val daysInMonth = currentMonth.lengthOfMonth()

        var dayCounter = 1
        for (week in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dayIndex in 0 until 7) {
                    val currentDayNumber = dayCounter
                    if ((week == 0 && dayIndex < firstDayOfMonth) || dayCounter > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val dateObject = currentMonth.withDayOfMonth(currentDayNumber)
                        val isToday = dateObject == today
                        val isSelected = dateObject == parsedDate

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .background(
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primaryContainer
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onDateSelected(dateObject) }
                                .pointerHoverIcon(PointerIcon.Hand),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$currentDayNumber",
                                color = if (isSelected) Color.White else Color.Black,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        dayCounter++
                    }
                }
            }
        }
    }
}
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen

@Composable
fun AwakeningTextChallenge(
    targetText: String,
    strictCase: Boolean = false,
    onChallengeCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var userInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val isMatched = remember(userInput, targetText, strictCase) {
        if (strictCase) {
            userInput == targetText
        } else {
            userInput.trim().equals(targetText.trim(), ignoreCase = true)
        }
    }

    LaunchedEffect(isMatched) {
        if (isMatched) {
            keyboardController?.hide()
        }
    }

    val buttonColor by animateColorAsState(
        targetValue = if (isMatched) SuccessGreen else Color(0xFF475569),
        label = "ButtonColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xCC0F172A)
        ),
        shape = RoundedCornerShape(24.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isMatched) SuccessGreen else Color(0xFF334155)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Badge
            Surface(
                color = if (isMatched) SuccessGreen.copy(alpha = 0.2f) else Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isMatched) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isMatched) SuccessGreen else Color(0xFFFBBF24),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMatched) "AWAKENING VERIFIED!" else "AWAKENING CHALLENGE",
                        color = if (isMatched) SuccessGreen else Color(0xFFFBBF24),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Target Text Display with Quote Styling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = "Quote",
                        tint = Color(0xFFD97706),
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Start)
                    )

                    Text(
                        text = targetText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Letter-by-letter progress comparison
                    val annotatedComparison = buildAnnotatedString {
                        val targetLen = targetText.length
                        val inputLen = userInput.length

                        for (i in 0 until targetLen) {
                            if (i < inputLen) {
                                val userChar = userInput[i]
                                val targetChar = targetText[i]
                                val matches = if (strictCase) {
                                    userChar == targetChar
                                } else {
                                    userChar.equals(targetChar, ignoreCase = true)
                                }

                                if (matches) {
                                    withStyle(SpanStyle(color = SuccessGreen, fontWeight = FontWeight.Bold)) {
                                        append(targetChar)
                                    }
                                } else {
                                    withStyle(SpanStyle(color = ErrorRed, fontWeight = FontWeight.Bold)) {
                                        append(userChar)
                                    }
                                }
                            } else {
                                withStyle(SpanStyle(color = Color(0xFF64748B))) {
                                    append(targetText[i])
                                }
                            }
                        }
                    }

                    Text(
                        text = annotatedComparison,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text Input Field
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("awakening_text_input"),
                placeholder = {
                    Text(
                        text = "Type the sentence above exactly...",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                },
                singleLine = false,
                maxLines = 3,
                trailingIcon = {
                    if (userInput.isNotEmpty()) {
                        IconButton(onClick = { userInput = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear input",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedBorderColor = if (isMatched) SuccessGreen else Color(0xFFFBBF24),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (isMatched) {
                            onChallengeCompleted()
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress indicator
            val matchedCount = userInput.take(targetText.length).zip(targetText).count { (u, t) ->
                if (strictCase) u == t else u.equals(t, ignoreCase = true)
            }
            val totalCount = targetText.length
            val progressFraction = (matchedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isMatched) SuccessGreen else Color(0xFFD97706),
                trackColor = Color(0xFF334155)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$matchedCount / $totalCount matched",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = if (isMatched) "Ready to Stop!" else "Type exact text to unlock",
                    color = if (isMatched) SuccessGreen else Color(0xFFFBBF24),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // STOP ALARM BUTTON - STRICT REQUIREMENT: Disabled until exact match!
            Button(
                onClick = {
                    if (isMatched) {
                        onChallengeCompleted()
                    }
                },
                enabled = isMatched,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("stop_alarm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF334155),
                    disabledContentColor = Color(0xFF64748B)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AlarmOff,
                    contentDescription = "Stop Alarm",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMatched) "STOP ALARM NOW" else "LOCKED — TYPE SENTENCE TO STOP",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

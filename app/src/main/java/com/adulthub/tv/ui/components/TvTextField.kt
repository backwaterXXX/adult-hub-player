package com.adulthub.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.CardSurfaceFocused
import com.adulthub.tv.ui.theme.FocusBorder
import com.adulthub.tv.ui.theme.TextMuted
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String = "",
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
) {
    var isFocused by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editorHadFocus by remember { mutableStateOf(false) }
    val editFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) editFocusRequester.requestFocus()
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused || isEditing) FocusBorder else TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isFocused) CardSurfaceFocused else CardSurface,
                    shape = RoundedCornerShape(8.dp),
                )
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) FocusBorder else Color(0xFF333333),
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (isEditing) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(editFocusRequester)
                        .onFocusChanged {
                            isFocused = it.isFocused
                            if (it.isFocused) {
                                editorHadFocus = true
                            } else if (editorHadFocus) {
                                editorHadFocus = false
                                isEditing = false
                            }
                        },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(FocusBorder),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    singleLine = singleLine,
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty() && hint.isNotEmpty()) {
                                Text(
                                    text = hint,
                                    style = TextStyle(color = TextMuted, fontSize = 16.sp),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editorHadFocus = false
                            isEditing = true
                        }
                        .onFocusChanged { isFocused = it.isFocused },
                ) {
                    val displayValue = visualTransformation
                        .filter(AnnotatedString(value))
                        .text
                        .text
                    Text(
                        text = displayValue.ifEmpty { hint },
                        style = TextStyle(
                            color = if (displayValue.isEmpty()) TextMuted else TextPrimary,
                            fontSize = 16.sp,
                        ),
                    )
                }
            }
        }
    }
}

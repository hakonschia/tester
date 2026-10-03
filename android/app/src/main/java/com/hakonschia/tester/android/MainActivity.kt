package com.hakonschia.tester.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Button(
                onClick = {},
                modifier = Modifier
                    .testTag("button")
            ) {
                Text("Hey")
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                for (i in 0..150) {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .testTag("button-$i")
                    ) {
                        Text("Hey $i")
                    }
                }
            }
        }
    }
}
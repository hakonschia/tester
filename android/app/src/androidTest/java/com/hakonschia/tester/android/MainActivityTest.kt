package com.hakonschia.tester.android

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun succeedingTest() {
        rule.onNodeWithTag("button").assertExists()
    }

    @Test
    fun failingTest() {
        rule.onNodeWithTag("non-existing-button").assertExists()
    }

    @Test
    fun longTest() {
        for (i in 0..150) {
            rule.onRoot().performScrollToNode(hasTestTag("button-$i"))
        }
        for (i in 150 downTo 0) {
            rule.onRoot().performScrollToNode(hasTestTag("button-$i"))
        }
    }
}
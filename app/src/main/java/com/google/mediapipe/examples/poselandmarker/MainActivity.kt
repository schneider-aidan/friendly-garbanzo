/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.poselandmarker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel : MainViewModel by viewModels()
    private var isProgrammaticNavigation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController

        activityMainBinding.navigation.setOnItemSelectedListener { item ->

            if (isProgrammaticNavigation) {
                isProgrammaticNavigation = false
                return@setOnItemSelectedListener true
            }

            when (item.itemId) {

                R.id.gallery_fragment -> {

                    showPasswordDialog { success ->
                        if (success) {
                            isProgrammaticNavigation = true
                            navController.navigate(R.id.gallery_fragment)
                            activityMainBinding.navigation.selectedItemId = R.id.gallery_fragment
                        }
                    }

                    false
                }

                else -> {
                    navController.navigate(item.itemId)
                    true
                }
            }
        }
        activityMainBinding.navigation.setOnNavigationItemReselectedListener {
            // ignore the reselection
        }
    }

    override fun onBackPressed() {
        finish()
    }

    private fun showPasswordDialog(onResult: (Boolean) -> Unit) {

        val editText = android.widget.EditText(this)
        editText.hint = "Enter master password"
        editText.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enter Master Password")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
                onResult(false)
            }
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)

            button.setOnClickListener {
                val input = editText.text.toString()

                if (input == "password") {
                    dialog.dismiss()
                    onResult(true)
                } else {
                    editText.error = "Incorrect password. Try again."
                }
            }
        }

        dialog.show()
    }
}
package com.neodemus.myhrtest

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neodemus.myhrtest.ui.theme.MyHrTestTheme


class MainActivity : ComponentActivity() {

    private val heartRateViewModel: HeartRateViewModel by viewModels()
    private val vo2MaxViewModel: Vo2MaxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Bluetooth permissions
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Handle permission results if needed
        }

        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        permissionLauncher.launch(requiredPermissions)

        setContent {
            MyHrTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "hr") {
                        composable("hr") {
                            HeartRateScreen(viewModel = heartRateViewModel, navController = navController)
                        }
                        composable("vo2max") {
                            Vo2MaxScreen(viewModel = vo2MaxViewModel, navController = navController)
                        }
                        composable("connections") {
                            ConnectionSettingsScreen(
                                heartRateViewModel = heartRateViewModel,
                                vo2MaxViewModel = vo2MaxViewModel,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeartRateScreen(viewModel: HeartRateViewModel, navController: NavController) {
    val heartRate by viewModel.heartRate.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "HR Monitor",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "$heartRate",
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "BPM",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Device: $connectedDevice",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Status: $connectionState",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { navController.navigate("connections") }) {
            Text("Connections")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { navController.navigate("vo2max") }) {
            Text("Go to VO2max")
        }
    }
}

@Composable
fun Vo2MaxScreen(viewModel: Vo2MaxViewModel, navController: NavController) {
    val vo2 by viewModel.vo2.collectAsState()
    val vco2 by viewModel.vco2.collectAsState()
    val rq by viewModel.rq.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "VO2max Monitor",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "VO2: ${String.format("%.2f", vo2)}", fontSize = 40.sp)
        Text(text = "VCO2: ${String.format("%.2f", vco2)}", fontSize = 40.sp)
        Text(text = "RQ: ${String.format("%.2f", rq)}", fontSize = 40.sp)


        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Device: $connectedDevice",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Status: $connectionState",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { navController.navigate("connections") }) {
            Text("Connections")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { navController.navigate("hr") }) {
            Text("Go to Heart Rate")
        }
    }
}

@Composable
fun ConnectionSettingsScreen(
    heartRateViewModel: HeartRateViewModel,
    vo2MaxViewModel: Vo2MaxViewModel,
    navController: NavController
) {
    val heartRateState by heartRateViewModel.connectionState.collectAsState()
    val vo2State by vo2MaxViewModel.connectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Connections",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Heart Rate: $heartRateState", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { heartRateViewModel.startScan() }) {
            Text("Connect Heart Rate Monitor")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "VO2: $vo2State", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { vo2MaxViewModel.startScan() }) {
            Text("Connect VO2 Sensor")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { navController.popBackStack() }) {
            Text("Back")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HeartRateScreenPreview() {
    MyHrTestTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "HR Monitor", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "75", fontSize = 80.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(text = "BPM", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Device..", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Status: Disconnected", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { }) {
                Text("Connections")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { }) {
                Text("Go to VO2max")
            }
        }
    }
}

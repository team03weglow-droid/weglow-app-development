package com.example.weglow.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.weglow.R
import com.example.weglow.core.image.ProfileCameraImageStore
import com.example.weglow.core.image.ProfileImageReader
import com.example.weglow.domain.model.ProfileImageUpload
import com.example.weglow.ui.components.ProfileAvatar
import com.example.weglow.ui.components.rememberDecodedBitmap
import com.example.weglow.ui.theme.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    displayName: String? = null,
    profileImage: ByteArray? = null,
    isUploadingImage: Boolean = false,
    imageError: String? = null,
    onProfileImagePicked: (ProfileImageUpload) -> Unit = {},
    onProfileImageUnreadable: () -> Unit = {},
    onConsumeImageError: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarBitmap = rememberDecodedBitmap(profileImage)
    val fileProviderAuthority = "${context.packageName}.fileprovider"

    var showSourceChooser by rememberSaveable { mutableStateOf(false) }
    // Absolute path of the throw-away file the system camera writes into. Held
    // in saveable state so an in-progress capture survives a configuration
    // change / process death while the camera app is in the foreground.
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    // Screen-local, non-error notice (e.g. camera permission declined). This is
    // never a ProfileViewModel error: declining or cancelling is not a failure.
    var cameraNotice by remember { mutableStateOf<String?>(null) }

    // Camera and gallery both hand their picked image to the exact same Phase 6
    // pipeline: ProfileImageReader -> ProfileImageUpload -> onProfileImagePicked
    // (-> ProfileViewModel -> ProfileImageRepository -> Supabase Storage).
    fun submitPickedImage(uri: Uri) {
        scope.launch {
            val upload = withContext(Dispatchers.IO) {
                ProfileImageReader.read(context.contentResolver, uri)
            }
            if (upload != null) onProfileImagePicked(upload) else onProfileImageUnreadable()
        }
    }

    // Modern Photo Picker: no runtime storage permission required.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) submitPickedImage(uri)
    }

    // System camera (ACTION_IMAGE_CAPTURE). `saved` is true only when the camera
    // app actually wrote a picture into the file we supplied.
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val file = pendingCapturePath?.let(::File)
        pendingCapturePath = null
        if (saved && file != null) {
            val captureUri = FileProvider.getUriForFile(context, fileProviderAuthority, file)
            scope.launch {
                val upload = withContext(Dispatchers.IO) {
                    try {
                        ProfileImageReader.read(context.contentResolver, captureUri)
                    } finally {
                        // The temp capture is never the persisted picture; drop it
                        // as soon as its bytes are read into ProfileImageUpload.
                        ProfileCameraImageStore.discard(file)
                    }
                }
                if (upload != null) onProfileImagePicked(upload) else onProfileImageUnreadable()
            }
        } else {
            // Backed out of the camera, or nothing captured: clean up quietly.
            ProfileCameraImageStore.discard(file)
        }
    }

    fun launchCamera() {
        val file = ProfileCameraImageStore.createCaptureFile(context.cacheDir)
        pendingCapturePath = file.absolutePath
        takePhoto.launch(FileProvider.getUriForFile(context, fileProviderAuthority, file))
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            cameraNotice = "Camera access is off. You can still choose a photo from your gallery."
        }
    }

    fun onTakePhotoSelected() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    // Auto-dismiss transient upload/validation errors after they have been shown.
    LaunchedEffect(imageError) {
        if (imageError != null) {
            delay(4000)
            onConsumeImageError()
        }
    }

    LaunchedEffect(cameraNotice) {
        if (cameraNotice != null) {
            delay(4000)
            cameraNotice = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.weglow_logo),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            ProfileAvatar(image = avatarBitmap, size = 36.dp)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.clickable(enabled = !isUploadingImage) {
                    showSourceChooser = true
                }
            ) {
                ProfileAvatar(image = avatarBitmap, size = 120.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Clay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "Change profile picture",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (isUploadingImage) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(displayName ?: "Your Profile", fontFamily = JungeFont, fontSize = 30.sp, color = TextBlack)
            Spacer(modifier = Modifier.height(4.dp))
            Text("GLOW MEMBER", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)

            if (imageError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    imageError,
                    fontFamily = JungeFont,
                    fontSize = 12.sp,
                    color = LogoutRed,
                    modifier = Modifier.clickable(onClick = onConsumeImageError)
                )
            }

            if (cameraNotice != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    cameraNotice ?: "",
                    fontFamily = JungeFont,
                    fontSize = 12.sp,
                    color = SoftGray,
                    modifier = Modifier.clickable { cameraNotice = null }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Skin Score", fontFamily = JungeFont, fontSize = 20.sp, color = TextBlack)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        // No real skin-scoring measurement exists yet (a scan only yields
                        // acne detections, never a numeric score) - never claim otherwise.
                        "Complete a skin scan to see your results here.",
                        fontFamily = JungeFont,
                        fontSize = 13.sp,
                        color = SoftGray
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("View Details", fontFamily = JungeFont, fontSize = 12.sp, color = TextBlack)
                    }
                }
                Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = TrackGray,
                            radius = size.minDimension / 2 - 6.dp.toPx(),
                            style = Stroke(width = 6.dp.toPx())
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("--", fontFamily = JungeFont, fontSize = 22.sp, color = TextBlack)
                        Text("/100", fontFamily = JungeFont, fontSize = 11.sp, color = SoftGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Skin Analysis", fontFamily = JungeFont, fontSize = 24.sp, color = TextBlack)
                Text("See all", fontFamily = JungeFont, fontSize = 14.sp, color = SoftGray)
            }
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
                    .clickable { }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(CardGray, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TextBlack)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Latest Scan", fontFamily = JungeFont, fontSize = 16.sp, color = TextBlack)
                    // No scan history is persisted by the app yet, so this must never invent a
                    // date or a detected concern for a scan that may not have happened.
                    Text("No scan yet. Analyze your skin to see results here.", fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                "My Skincare Shelf",
                fontFamily = JungeFont,
                fontSize = 24.sp,
                color = TextBlack,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                ShelfCard(
                    title = "Morning Routine",
                    // The real step count lives on the Routines tab and can vary per user;
                    // never restate a specific item count here that the routine may not match.
                    itemCount = "View steps",
                    imageRes = R.drawable.shelf_morning_routine,
                    modifier = Modifier.weight(1f)
                )
                ShelfCard(
                    title = "Night Repair",
                    itemCount = "View steps",
                    imageRes = R.drawable.shelf_night_repair,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
            ) {
                SettingsRow(icon = Icons.Default.ShoppingBag, label = "Order History")
                HorizontalDivider(color = TrackGray)
                SettingsRow(icon = Icons.Default.FavoriteBorder, label = "Saved Products")
                HorizontalDivider(color = TrackGray)
                SettingsRow(icon = Icons.Default.Person, label = "Account Settings")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Log Out",
                fontFamily = JungeFont,
                fontSize = 16.sp,
                color = LogoutRed,
                modifier = Modifier.clickable(onClick = onLogout)
            )

            Spacer(modifier = Modifier.height(100.dp))
        }

        if (showSourceChooser) {
            ModalBottomSheet(
                onDismissRequest = { showSourceChooser = false },
                containerColor = CardWhite,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Update profile picture",
                        fontFamily = JungeFont,
                        fontSize = 18.sp,
                        color = TextBlack
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ImageSourceRow(
                        icon = Icons.Default.PhotoCamera,
                        label = "Take Photo",
                    ) {
                        showSourceChooser = false
                        onTakePhotoSelected()
                    }
                    ImageSourceRow(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Choose From Gallery",
                    ) {
                        showSourceChooser = false
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = DarkGreen)
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, fontFamily = JungeFont, fontSize = 16.sp, color = TextBlack)
    }
}

@Composable
private fun ShelfCard(title: String, itemCount: String, imageRes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(12.dp)
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, fontFamily = JungeFont, fontSize = 15.sp, color = TextBlack)
        Text(itemCount, fontFamily = JungeFont, fontSize = 12.sp, color = SoftGray)
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = DarkGreen)
            Spacer(modifier = Modifier.width(14.dp))
            Text(label, fontFamily = JungeFont, fontSize = 16.sp, color = TextBlack)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SoftGray)
    }
}

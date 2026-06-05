package com.snapscan.app;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.snapscan.app.R;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SnapscanMainActivity";
    private ActivityResultLauncher<IntentSenderRequest> scannerLauncher;
    private ImageView scannedImageView;
    private TextView ocrResultTextView;
    private View ocrButton;
    private View shareButton;
    private View tagsLayout;
    private View saveButton;
    private Uri lastScannedImageUri;
    private Uri lastScannedPdfUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        View mainView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        scannedImageView = findViewById(R.id.scannedImage);
        ocrResultTextView = findViewById(R.id.ocrResultText);
        ocrButton = findViewById(R.id.ocrButton);
        shareButton = findViewById(R.id.shareButton);
        tagsLayout = findViewById(R.id.tagsLayout);
        saveButton = findViewById(R.id.saveButton);
        // Scrolling is handled by the outer ScrollView

        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();

        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);

        scannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        GmsDocumentScanningResult scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
                        if (scanningResult != null) {
                            List<GmsDocumentScanningResult.Page> pages = scanningResult.getPages();
                            if (pages != null && !pages.isEmpty()) {
                                lastScannedImageUri = pages.get(0).getImageUri();
                                scannedImageView.setImageURI(lastScannedImageUri);
                                scannedImageView.setVisibility(View.VISIBLE);
                                ocrButton.setVisibility(View.VISIBLE);
                                shareButton.setVisibility(View.VISIBLE);
                                tagsLayout.setVisibility(View.VISIBLE);
                                saveButton.setVisibility(View.VISIBLE);
                                ocrResultTextView.setVisibility(View.GONE);
                            }
                            
                            GmsDocumentScanningResult.Pdf pdf = scanningResult.getPdf();
                            if (pdf != null) {
                                lastScannedPdfUri = pdf.getUri();
                                Toast.makeText(this, "PDF saved: " + pdf.getUri().getLastPathSegment(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );

        findViewById(R.id.scanButton).setOnClickListener(v -> 
            scanner.getStartScanIntent(this)
                    .addOnSuccessListener(intentSender -> 
                        scannerLauncher.launch(new IntentSenderRequest.Builder(intentSender).build())
                    )
                    .addOnFailureListener(e -> 
                        Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    )
        );

        ocrButton.setOnClickListener(v -> performOcr());

        shareButton.setOnClickListener(v -> {
            if (lastScannedPdfUri != null) {
                Uri contentUri = lastScannedPdfUri;
                if ("file".equals(lastScannedPdfUri.getScheme())) {
                    File file = new File(lastScannedPdfUri.getPath());
                    contentUri = FileProvider.getUriForFile(this, "com.snapscan.app.fileprovider", file);
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/pdf");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.setClipData(ClipData.newRawUri(null, contentUri));
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share Scanned PDF"));
            } else if (lastScannedImageUri != null) {
                Uri contentUri = lastScannedImageUri;
                if ("file".equals(lastScannedImageUri.getScheme())) {
                    File file = new File(lastScannedImageUri.getPath());
                    contentUri = FileProvider.getUriForFile(this, "com.snapscan.app.fileprovider", file);
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/jpeg");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.setClipData(ClipData.newRawUri(null, contentUri));
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share Scanned Document"));
            }
        });

        saveButton.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.scan_saved), Toast.LENGTH_SHORT).show();
            // In a real app, we'd save the URI and tags to a database here
            resetUi();
        });
    }

    private void resetUi() {
        scannedImageView.setVisibility(View.GONE);
        ocrButton.setVisibility(View.GONE);
        shareButton.setVisibility(View.GONE);
        tagsLayout.setVisibility(View.GONE);
        saveButton.setVisibility(View.GONE);
        ocrResultTextView.setVisibility(View.GONE);
        lastScannedImageUri = null;
        lastScannedPdfUri = null;
    }

    private void performOcr() {
        if (lastScannedImageUri == null) return;

        try {
            InputImage image = InputImage.fromFilePath(this, lastScannedImageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String rawText = visionText.getText();
                        String resultToDisplay = rawText.isEmpty() ? getString(R.string.no_text_detected) : rawText;
                        ocrResultTextView.setText(resultToDisplay);
                        ocrResultTextView.setVisibility(View.VISIBLE);
                        Toast.makeText(this, getString(R.string.ocr_completed), Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "OCR Failed", e);
                        Toast.makeText(this, "OCR Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } catch (IOException e) {
            Log.e(TAG, "Error loading image for OCR", e);
        }
    }
}

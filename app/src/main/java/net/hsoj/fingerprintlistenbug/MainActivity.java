package net.hsoj.fingerprintlistenbug;

import android.graphics.Color;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintManager.AuthenticationCallback;
import android.hardware.fingerprint.FingerprintManager.AuthenticationResult;
import android.hardware.fingerprint.FingerprintManager.CryptoObject;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * This Activity demonstrates a strange interaction between the use of fingerprint authentication
 * in this app and the Android system lock screen.
 *
 * Under certain conditions, it seems possible for an app that is listening for fingerprints to
 * prevent the system lock screen from being unlocked via the fingerprint reader. The lock screen
 * appears to be listening for prints, but no feedback is provided by the UI or vibrator when a
 * finger is tapped on the sensor.
 *
 * This behavior can be reproduced fairly consistently by building and running the app while the
 * screen is off. It's also possible to repro by launching the app and hitting the power button at
 * just the right time, but this method is less reliable.
 */
@SuppressWarnings("MissingPermission")
public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_NAME = "net.hsoj.fingerprintlistenbug.key";

    private ImageView mImageView;

    private FingerprintManager mFingerprintManager;
    private KeyStore mKeyStore;
    private KeyGenerator mKeyGenerator;
    private Cipher mCipher;
    private CancellationSignal mCancellationSignal;
    private boolean mIsCancelled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.fingerprint);

        mFingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);

        try {
            mKeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);

            mKeyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);

            String transformation = String.format("%s/%s/%s",
                    KeyProperties.KEY_ALGORITHM_AES,
                    KeyProperties.BLOCK_MODE_CBC,
                    KeyProperties.ENCRYPTION_PADDING_PKCS7);

            mCipher = Cipher.getInstance(transformation);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        createKey();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");

        try {
            if (initCipher()) {
                startListening();
            } else {
                Log.d(TAG, "Key invalidated.");
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");

        stopListening();
    }

    private void createKey() {
        Log.v(TAG, "createKey");

        try {
            mKeyStore.load(null);
            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build();
            mKeyGenerator.init(keyGenParameterSpec);
            mKeyGenerator.generateKey();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create key.", e);
        }
    }

    private boolean initCipher() throws Exception {
        Log.v(TAG, "initCipher");

        try {
            mKeyStore.load(null);
            SecretKey key = (SecretKey) mKeyStore.getKey(KEY_NAME, null);
            mCipher.init(Cipher.ENCRYPT_MODE, key);

            return true;
        } catch (KeyPermanentlyInvalidatedException e) {
            return false;
        }
    }

    private void startListening() {
        Log.v(TAG, "startListening");

        if (!(mFingerprintManager.isHardwareDetected() &&
                mFingerprintManager.hasEnrolledFingerprints())) {
            Log.e(TAG, "Hardware not detected or no enrolled prints.");
            return;
        }

        mCancellationSignal = new CancellationSignal();
        mIsCancelled = false;
        CryptoObject cryptoObject = new CryptoObject(mCipher);
        mFingerprintManager.authenticate(
                cryptoObject, mCancellationSignal, 0, mAuthenticationCallback, null);

        mImageView.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Listening for fingerprint", Toast.LENGTH_LONG).show();
    }

    private void stopListening() {
        Log.v(TAG, "stopListening");

        if (!mIsCancelled) {
            mIsCancelled = true;
            if (mCancellationSignal != null) {
                mCancellationSignal.cancel();
                mCancellationSignal = null;
            }
        }

        mImageView.setVisibility(View.INVISIBLE);
        Toast.makeText(this, "Stopped listening", Toast.LENGTH_LONG).show();
    }

    private AuthenticationCallback mAuthenticationCallback = new AuthenticationCallback() {
        @Override
        public void onAuthenticationError(int errorCode, CharSequence errString) {
            super.onAuthenticationError(errorCode, errString);

            mImageView.setColorFilter(Color.RED);
            Log.e(TAG, errString.toString());
        }

        @Override
        public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
            super.onAuthenticationHelp(helpCode, helpString);

            Log.e(TAG, helpString.toString());
        }

        @Override
        public void onAuthenticationSucceeded(AuthenticationResult result) {
            super.onAuthenticationSucceeded(result);

            mImageView.setColorFilter(Color.GREEN);
            Log.d(TAG, "Authentication succeeded!");
            Toast.makeText(MainActivity.this,
                    "Authentication succeeded!", Toast.LENGTH_LONG).show();
        }

        @Override
        public void onAuthenticationFailed() {
            super.onAuthenticationFailed();

            Log.e(TAG, "Authentication failed!");
            Toast.makeText(MainActivity.this,
                    "Authentication failed!", Toast.LENGTH_LONG).show();
        }
    };
}

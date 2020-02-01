package com.example.fishrecognition;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.esafirm.imagepicker.features.ImagePicker;
import com.esafirm.imagepicker.model.Image;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.List;
import java.util.Random;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.display_image_iv) ImageView imageView;
    @BindView(R.id.condition_tv) TextView condition;
    @BindView(R.id.valid_time_tv) TextView validTime;
    @BindView(R.id.price) TextView price;
    @BindView(R.id.description) TextView description;

    Random rand = new Random();

    @OnClick(R.id.take_photo_btn) void onTakePhoto() {
        //check permission dulu coy
        Dexter.withActivity(MainActivity.this)
                .withPermission(Manifest.permission.CAMERA)
                .withListener(new PermissionListener() {
                    @Override public void onPermissionGranted(PermissionGrantedResponse response) {
                        ImagePicker.cameraOnly().start(MainActivity.this);
                    }
                    @Override public void onPermissionDenied(PermissionDeniedResponse response) {
                        Toast.makeText(MainActivity.this, "Please grant camera permission", Toast.LENGTH_LONG).show();
                    }
                    @Override public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {/* ... */}
                }).check();
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (ImagePicker.shouldHandle(requestCode, resultCode, data)) {
            // Get a list of picked images
            List<Image> images = ImagePicker.getImages(data);
            // or get a single image only
            Image image = ImagePicker.getFirstImageOrNull(data);
            Glide.with(this).load(image.getPath()).into(imageView);
            boolean cond = generateCondition();
            condition.setText(cond ? "Segar" : "Tidak Segar");
            int valid = generateValidateTime(cond);
            validTime.setText(valid + " Hari");
            price.setText(generatePrice(cond, valid));
            description.setText(generateDescription(cond));
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public boolean generateCondition() {
        int selectedIndex = rand.nextInt(2);
        return selectedIndex == 1;
    }

    public int generateValidateTime(boolean condition) {
        int conditionLenght = 1 + rand.nextInt(condition ? 7 : 3);
        return conditionLenght;
    }

    public String generatePrice(boolean condition, int valid) {
        int startPrice = 20;
        if(condition) {
            valid *= 2;
            startPrice += valid;
        }
        int price = (startPrice + rand.nextInt(startPrice * 2)) * 500;
        NumberFormat format = NumberFormat.getCurrencyInstance();
        format.setMaximumFractionDigits(0);
        format.setCurrency(Currency.getInstance("IDR"));
        return format.format(price);
    }

    public String generateDescription(boolean condition) {
        if(condition) {
            return "Ikan segar yang memiliki banyak manfaat";
        } else {
            return "Ikan tidak segar yang kurang manfaat";
        }
    }
}

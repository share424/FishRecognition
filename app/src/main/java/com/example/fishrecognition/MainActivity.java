package com.example.fishrecognition;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Random;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat;

public class MainActivity extends AppCompatActivity {

    public static final String MODEL_PATH = "model_fish.tflite";
    public static final String LABEL_PATH = "labels.txt";

    @BindView(R.id.display_image_iv) ImageView imageView;
    @BindView(R.id.condition_tv) TextView condition;
    @BindView(R.id.valid_time_tv) TextView validTime;
    @BindView(R.id.price) TextView price;
    @BindView(R.id.description) TextView description;
    @BindView(R.id.confident) TextView confident;
    @BindView(R.id.fish) TextView fish;

    Random rand = new Random();
    private MappedByteBuffer tfliteModel;
    private Interpreter tflite;
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    private List<String> labels;

    /** Image size along the x axis. */
    private int imageSizeX;

    /** Image size along the y axis. */
    private int imageSizeY;

    /** Input image TensorBuffer. */
    private TensorImage inputImageBuffer;

    /** Output probability TensorBuffer. */
    private TensorBuffer outputProbabilityBuffer;

    /** Processer to apply post processing of the output probability. */
    private TensorProcessor probabilityProcessor;

    /**
     * The quantized model does not require normalization, thus set mean as 0.0f, and std as 1.0f to
     * bypass the normalization.
     */
    private static final float IMAGE_MEAN = 127.5f;

    private static final float IMAGE_STD = 127.5f;

    /** Quantized MobileNet requires additional dequantization to the output probability. */
    private static final float PROBABILITY_MEAN = 0.0f;

    private static final float PROBABILITY_STD = 1.0f;

    private List<Fish> fishList = new ArrayList<>();

    @OnClick(R.id.take_photo_btn) void onTakePhoto() {
        //check permission dulu coy
        Dexter.withActivity(MainActivity.this)
                .withPermission(Manifest.permission.CAMERA)
                .withListener(new PermissionListener() {
                    @Override public void onPermissionGranted(PermissionGrantedResponse response) {
                        ImagePicker.cameraOnly().start(MainActivity.this);
//                        ImagePicker.create(MainActivity.this).start();
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
        initializeClassifier();
        initializeFish();
    }

    private void initializeFish() {
        Fish tongkol = new Fish("Tongkol", 30000, "Protein, Kalium, Magnesium, VIT A, dan VIT E");
        Fish gurame = new Fish("Gurame", 60000, "Protein Tinggi, VIT E, dan Kolestrol Rendah");
        Fish kakap_merah = new Fish("Kakap Merah", 50000, "Kalsium, VIT A, VIT D, VIT E, dan Protein");
        Fish nila = new Fish("Nila", 25000, "Protein Tinggi, Omega 3, Fosfor, VIT B12, VIT B5, dan Antioksidan");
        Fish mujair = new Fish("Mujair", 35000, "Protein, Asam Lemak Omega 3, Asam Lemak Omegra 6, dan Kalsium");
        fishList.add(kakap_merah);
        fishList.add(gurame);
        fishList.add(mujair);
        fishList.add(nila);
        fishList.add(tongkol);
    }

    private void initializeClassifier() {
        try {
            tfliteModel = FileUtil.loadMappedFile(this, MODEL_PATH);
            tflite = new Interpreter(tfliteModel);
            labels = FileUtil.loadLabels(this, LABEL_PATH);

            // Reads type and shape of input and output tensors, respectively.
            int imageTensorIndex = 0;
            int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}
            imageSizeY = imageShape[1];
            imageSizeX = imageShape[2];
            DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
            int probabilityTensorIndex = 0;
            int[] probabilityShape =
                    tflite.getOutputTensor(probabilityTensorIndex).shape(); // {1, NUM_CLASSES}
            DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType();

            // Creates the input tensor.
            inputImageBuffer = new TensorImage(DataType.FLOAT32);

            // Creates the output tensor and its processor.
            outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

            // Creates the post processor for the output probability.
            probabilityProcessor = new TensorProcessor.Builder().add(getPostprocessNormalizeOp()).build();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (ImagePicker.shouldHandle(requestCode, resultCode, data)) {
            // get a single image only
            Image image = ImagePicker.getFirstImageOrNull(data);
            Glide.with(this).load(image.getPath()).into(imageView);
            // for now generate random fish condition
            boolean cond = generateCondition();
            condition.setText(cond ? "Segar" : "Tidak Segar");
            // and generate random fish expired time
            int valid = generateValidateTime(cond);
            validTime.setText(valid + " Hari");
            // let's get the fish bitmap image
            Bitmap bmp = BitmapFactory.decodeFile(image.getPath());
            // and predict it
            Map<String, Float> probabilities = recognizeImage(bmp);
            float best = 0;
            String predictedLabel = labels.get(0);
            int idx = 0;
            int predictedIndex = 0;
            // find the best probability
            for(Map.Entry<String, Float> entry : probabilities.entrySet()) {
                if(entry.getValue() > best) {
                    best = entry.getValue();
                    // well because we use fish object, we didn't need the label anymore
                    predictedLabel = entry.getKey();
                    predictedIndex = idx;
                }
                idx++;
            }
            // set the labels on the screen
            fish.setText(fishList.get(predictedIndex).getName());
            price.setText(generatePrice(fishList.get(predictedIndex).getPrice()));
            confident.setText(String.format("%.2f",best*100)+"%");
            description.setText(fishList.get(predictedIndex).getDescription());

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public boolean generateCondition() {
        int selectedIndex = rand.nextInt(2);
        return selectedIndex == 1;
    }

    public int generateValidateTime(boolean condition) {
        return 1 + rand.nextInt(condition ? 7 : 3);
    }

    public String generatePrice(int price) {
        NumberFormat format = NumberFormat.getCurrencyInstance();
        format.setMaximumFractionDigits(0);
        format.setCurrency(Currency.getInstance("IDR"));
        return format.format(price);
    }

    /** Loads input image, and applies preprocessing. */
    private TensorImage loadImage(final Bitmap bitmap) {
        // Loads bitmap into a TensorImage.
        inputImageBuffer.load(bitmap);

        // Creates processor for the TensorImage.
        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
        // the screen always in portrait
        int numRoration = 0 / 90;
        // TODO(b/143564309): Fuse ops inside ImageProcessor.
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
                        .add(new ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                        .add(new Rot90Op(numRoration))
                        .add(getPreprocessNormalizeOp())
                        .build();
        return imageProcessor.process(inputImageBuffer);
    }

    protected TensorOperator getPreprocessNormalizeOp() {
        return new NormalizeOp(IMAGE_MEAN, IMAGE_STD);
    }

    protected TensorOperator getPostprocessNormalizeOp() {
        return new NormalizeOp(PROBABILITY_MEAN, PROBABILITY_STD);
    }

    /** Runs inference and returns the classification results. */
    public Map<String, Float> recognizeImage(final Bitmap bitmap) {
        inputImageBuffer = loadImage(bitmap);


        tflite.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());

        // Gets the map of label and probability.
        Map<String, Float> labeledProbability =
                new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                        .getMapWithFloatValue();
        return labeledProbability;
    }

}

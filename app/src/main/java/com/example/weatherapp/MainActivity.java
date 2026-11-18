package com.codingtutorials.weatherapp;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private TextView cityNameText, temperatureText, humidityText, descriptionText, windText;
    private TextView dateText, highLowText, feelsLikeText;
    private ImageView weatherIcon;
    private Button refreshButton;
    private EditText cityNameInput;
    private ProgressBar loadingBar;
    private View rootLayout;
    private static final String API_KEY = BuildConfig.OPENWEATHER_API_KEY;

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.rootLayout);
        cityNameText = findViewById(R.id.cityNameText);
        dateText = findViewById(R.id.dateText);
        temperatureText = findViewById(R.id.temperatureText);
        highLowText = findViewById(R.id.highLowText);
        humidityText = findViewById(R.id.humidityText);
        windText = findViewById(R.id.windText);
        feelsLikeText = findViewById(R.id.feelsLikeText);
        descriptionText = findViewById(R.id.descriptionText);
        weatherIcon = findViewById(R.id.weatherIcon);
        refreshButton = findViewById(R.id.fetchWeatherButton);
        cityNameInput = findViewById(R.id.cityNameInput);
        loadingBar = findViewById(R.id.loadingBar);

        applyTheme(true);

        refreshButton.setOnClickListener(view -> search());
        cityNameInput.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });

        FetchWeatherData("Mumbai");
    }

    private void search() {
        String cityName = cityNameInput.getText().toString().trim();
        if (!cityName.isEmpty()) {
            hideKeyboard();
            FetchWeatherData(cityName);
        } else {
            cityNameInput.setError("Please enter a city name");
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(cityNameInput.getWindowToken(), 0);
        cityNameInput.clearFocus();
    }

    private void FetchWeatherData(String cityName) {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.openweathermap.org")
                .addPathSegments("data/2.5/weather")
                .addQueryParameter("q", cityName)
                .addQueryParameter("appid", API_KEY)
                .addQueryParameter("units", "metric")
                .build();

        loadingBar.setVisibility(View.VISIBLE);
        executorService.execute(() -> {
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                String result = response.body() != null ? response.body().string() : null;
                boolean ok = response.isSuccessful();
                runOnUiThread(() -> {
                    loadingBar.setVisibility(View.GONE);
                    if (ok) {
                        updateUI(result);
                    } else if (response.code() == 404) {
                        showError("City not found");
                    } else {
                        showError("Couldn't load weather (" + response.code() + ")");
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    loadingBar.setVisibility(View.GONE);
                    showError("No internet connection");
                });
            }
        });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void updateUI(String result) {
        if (result == null) return;
        try {
            JSONObject jsonObject = new JSONObject(result);
            JSONObject main = jsonObject.getJSONObject("main");
            double temperature = main.getDouble("temp");
            double feelsLike = main.getDouble("feels_like");
            double tempMin = main.getDouble("temp_min");
            double tempMax = main.getDouble("temp_max");
            double humidity = main.getDouble("humidity");
            // OpenWeatherMap returns m/s in metric units
            double windKmh = jsonObject.getJSONObject("wind").getDouble("speed") * 3.6;

            JSONObject weather = jsonObject.getJSONArray("weather").getJSONObject(0);
            String description = weather.getString("description");
            String iconCode = weather.getString("icon");

            int resId = getResources().getIdentifier("ic_" + iconCode, "drawable", getPackageName());
            if (resId != 0) weatherIcon.setImageResource(resId);

            String country = jsonObject.optJSONObject("sys") != null
                    ? jsonObject.getJSONObject("sys").optString("country", "") : "";
            String name = jsonObject.getString("name");
            cityNameText.setText(country.isEmpty() ? name : name + ", " + country);

            temperatureText.setText(String.format(Locale.getDefault(), "%.0f°", temperature));
            highLowText.setText(String.format(Locale.getDefault(), "H: %.0f°   L: %.0f°", tempMax, tempMin));
            humidityText.setText(String.format(Locale.getDefault(), "%.0f%%", humidity));
            windText.setText(String.format(Locale.getDefault(), "%.0f km/h", windKmh));
            feelsLikeText.setText(String.format(Locale.getDefault(), "%.0f°", feelsLike));
            descriptionText.setText(capitalize(description));

            applyTheme(!iconCode.endsWith("n"));
        } catch (JSONException e) {
            e.printStackTrace();
            showError("Unexpected response");
        }
    }

    private void applyTheme(boolean isDay) {
        rootLayout.setBackgroundResource(isDay ? R.drawable.bg_day : R.drawable.bg_night);
        getWindow().setStatusBarColor(Color.parseColor(isDay ? "#4A90E2" : "#0F1C3F"));
        dateText.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}

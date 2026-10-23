package com.ai.apex;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private DrawerLayout drawer;
    private RecyclerView rvChat;
    private ChatAdapter adapter;
    private List<String[]> messages = new ArrayList<>();
    private EditText etMsg, etKey;
    private Spinner spinnerMode, spinnerModel;
    private TextView tvStatus, btnModeAI, btnModeTerminal, tvTerminal;
    private FrameLayout layoutAI;
    private LinearLayout layoutTerminal;
    private ScrollView scrollTerminal;
    private Switch swWeb, swSpeak;
    private SharedPreferences sp;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder().readTimeout(300, java.util.concurrent.TimeUnit.SECONDS).build();
    
    private int currentPermMode = 1;
    private String currentModel = "deepseek-chat";
    private int loopCount = 0;
    private TextToSpeech tts;
    private boolean isTerminalMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sp = getSharedPreferences("ApexData", Context.MODE_PRIVATE);

        drawer = findViewById(R.id.drawerLayout);
        rvChat = findViewById(R.id.rvChat);
        etMsg = findViewById(R.id.etMsg);
        etKey = findViewById(R.id.etKey);
        tvStatus = findViewById(R.id.tvStatus);
        spinnerMode = findViewById(R.id.spinnerMode);
        spinnerModel = findViewById(R.id.spinnerModel);
        swWeb = findViewById(R.id.swWeb);
        swSpeak = findViewById(R.id.swSpeak);
        
        btnModeAI = findViewById(R.id.btnModeAI);
        btnModeTerminal = findViewById(R.id.btnModeTerminal);
        layoutAI = findViewById(R.id.layoutAI);
        layoutTerminal = findViewById(R.id.layoutTerminal);
        tvTerminal = findViewById(R.id.tvTerminal);
        scrollTerminal = findViewById(R.id.scrollTerminal);

        com.google.android.material.floatingactionbutton.FloatingActionButton btnSend = findViewById(R.id.btnSend);
        ImageButton btnMenu = findViewById(R.id.btnMenu);
        ImageButton btnVoice = findViewById(R.id.btnVoice);
        Button btnSaveKey = findViewById(R.id.btnSaveKey);
        Button btnLogcat = findViewById(R.id.btnLogcat);
        Button btnApps = findViewById(R.id.btnApps);
        Button btnClean = findViewById(R.id.btnClean);

        tts = new TextToSpeech(this, status -> { if(status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.CHINESE); });

        adapter = new ChatAdapter(this, messages);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        etKey.setText(sp.getString("api_key", ""));
        addMessage("系统", "Apex 双模引擎启动。AI 智脑与真实终端已就绪。");

        String[] modes = {"普通模式", "ROOT 权限", "SHIZUKU 模式"};
        spinnerMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes));
        spinnerMode.setSelection(1);
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { currentPermMode = pos; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        String[] models = {"V3 极速", "R1 深度思考"};
        spinnerModel.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, models));
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { currentModel = (pos == 0) ? "deepseek-chat" : "deepseek-reasoner"; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // 模式切换
        btnModeAI.setOnClickListener(v -> {
            isTerminalMode = false;
            layoutAI.setVisibility(View.VISIBLE);
            layoutTerminal.setVisibility(View.GONE);
            btnModeAI.setTextColor(0xFF00F0FF);
            btnModeTerminal.setTextColor(0xFF64748B);
            etMsg.setHint("输入指令...");
            tvStatus.setText("AI 模式");
        });

        btnModeTerminal.setOnClickListener(v -> {
            isTerminalMode = true;
            layoutAI.setVisibility(View.GONE);
            layoutTerminal.setVisibility(View.VISIBLE);
            btnModeTerminal.setTextColor(0xFF00F0FF);
            btnModeAI.setTextColor(0xFF64748B);
            etMsg.setHint("输入 Linux 命令...");
            tvStatus.setText("终端模式");
        });

        btnMenu.setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        btnSaveKey.setOnClickListener(v -> { sp.edit().putString("api_key", etKey.getText().toString().trim()).apply(); Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show(); drawer.closeDrawer(GravityCompat.START); });
        
        // 侧滑栏快捷指令
        btnLogcat.setOnClickListener(v -> executeTerminal("logcat -d -t 200", "抓取日志", false));
        btnApps.setOnClickListener(v -> executeTerminal("pm list packages -3", "获取APP包名", false));
        btnClean.setOnClickListener(v -> executeTerminal("sync; echo 3 > /proc/sys/vm/drop_caches", "清理缓存", false));

        btnVoice.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            startActivityForResult(intent, 100);
        });

        btnSend.setOnClickListener(v -> {
            String msg = etMsg.getText().toString().trim();
            if (msg.isEmpty()) return;
            etMsg.setText("");
            
            if (isTerminalMode) {
                executeTerminal(msg, msg, false);
            } else {
                if (sp.getString("api_key", "").isEmpty()) { Toast.makeText(this, "请先在左侧配置 Key", Toast.LENGTH_SHORT).show(); return; }
                addMessage("我", msg);
                loopCount = 0;
                callAPI(msg, null, false);
            }
        });
    }

    @Override protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 100 && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) etMsg.setText(r.get(0));
        }
    }

    private void addMessage(String role, String content) {
        List<String[]> newMsgs = new ArrayList<>(messages);
        newMsgs.add(new String[]{role, content});
        adapter.updateMessages(newMsgs);
        rvChat.scrollToPosition(newMsgs.size() - 1);
    }

    private void updateLastMessage(String append) {
        if (messages.isEmpty()) return;
        String[] last = messages.get(messages.size() - 1);
        if (last[0].equals("AI") || last[0].equals("系统")) {
            last[1] += append;
            List<String[]> newMsgs = new ArrayList<>(messages);
            adapter.updateMessages(newMsgs);
            rvChat.scrollToPosition(messages.size() - 1);
        }
    }

    private void callAPI(String question, String execResult, boolean isLoop) {
        String key = sp.getString("api_key", "");
        if (!isLoop) addMessage("AI", "分析中...\n");
        uiHandler.post(() -> tvStatus.setText("思考中..."));

        try {
            JSONObject json = new JSONObject();
            json.put("model", currentModel);
            json.put("stream", true);
            JSONArray msgs = new JSONArray();
            String sys = "你是超级AI。可调用：[EXEC] 命令 或 [COMPILE] 语言\n代码\n[/COMPILE]。未完成禁止输出 [TASK_DONE]。";
            msgs.put(new JSONObject().put("role", "system").put("content", sys));
            msgs.put(new JSONObject().put("role", "user").put("content", question));
            if (isLoop && execResult != null) msgs.put(new JSONObject().put("role", "assistant").put("content", "上步结果：\n" + execResult + "\n继续或修复："));
            json.put("messages", msgs);

            Request req = new Request.Builder().url("https://api.deepseek.com/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .post(RequestBody.create(json.toString(), MediaType.parse("application/json"))).build();

            client.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { uiHandler.post(() -> tvStatus.setText("网络错误")); }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful() || response.body() == null) { uiHandler.post(() -> tvStatus.setText("API 错误")); return; }
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                        String line; StringBuilder full = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
                                JSONObject chunk = new JSONObject(line.substring(6));
                                JSONObject delta = chunk.getJSONArray("choices").getJSONObject(0).optJSONObject("delta");
                                if (delta != null && delta.has("reasoning_content")) updateLastMessage(delta.getString("reasoning_content"));
                                if (delta != null && delta.has("content") && !delta.getString("content").isEmpty()) {
                                    updateLastMessage(delta.getString("content")); full.append(delta.getString("content"));
                                }
                            }
                        }
                        uiHandler.post(() -> {
                            tvStatus.setText("就绪");
                            if (swSpeak.isChecked()) tts.speak(full.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
                        });
                        executeAction(full.toString(), question);
                    } catch (Exception e) { uiHandler.post(() -> tvStatus.setText("解析异常")); }
                }
            });
        } catch (Exception e) {}
    }

    private void executeAction(String reply, String originalQuestion) {
        if (reply.contains("[TASK_DONE]")) { addMessage("系统", "任务完美闭环 ✅"); return; }

        if (reply.contains("[EXEC]")) {
            int start = reply.indexOf("[EXEC]") + 6; int end = reply.indexOf("\n", start);
            String cmd = (end == -1 ? reply.substring(start) : reply.substring(start, end)).replace("`", "").trim();
            if (ShellEngine.isDangerous(cmd)) {
                uiHandler.post(() -> new AlertDialog.Builder(this).setTitle("⚠️ 高危警告").setMessage("AI 试图执行：\n" + cmd)
                    .setPositiveButton("放行", (d, w) -> executeTerminal(cmd, originalQuestion, true))
                    .setNegativeButton("拒绝", (d, w) -> callAPI(originalQuestion, "用户拒绝执行", true)).show());
                return;
            }
            executeTerminal(cmd, originalQuestion, true);
        }
    }

    // 双模终端执行 (isAI 标志决定是 AI 触发还是用户手打)
    private void executeTerminal(String cmd, String originalQuestion, boolean isAI) {
        if (isTerminalMode) {
            tvTerminal.append("\n$ " + cmd + "\n");
        } else {
            addMessage("系统", "执行: " + cmd);
        }
        
        new Thread(() -> {
            String res = ShellEngine.run(currentPermMode, cmd);
            uiHandler.post(() -> {
                if (isTerminalMode) {
                    tvTerminal.append(res + "\n$ ");
                    scrollTerminal.fullScroll(View.FOCUS_DOWN);
                } else {
                    addMessage("系统", "结果:\n" + res);
                    if (isAI && loopCount < 30) { loopCount++; callAPI(originalQuestion, res, true); }
                }
            });
        }).start();
    }
}

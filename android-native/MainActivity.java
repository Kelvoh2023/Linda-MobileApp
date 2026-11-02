package ke.linda.demo;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LindaSmsPlugin.class);
        super.onCreate(savedInstanceState);
        LindaSmsPlugin.setLaunch(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        LindaSmsPlugin.setLaunch(intent);
    }
}

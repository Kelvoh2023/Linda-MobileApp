package ke.linda.demo;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LindaSmsPlugin.class);
        super.onCreate(savedInstanceState);
    }
}

/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.reconfiguration.views;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import bftsmart.tom.util.io.StateCodecs;

/**
 *
 * @author eduardo
 */
public class DefaultViewStorage implements ViewStorage {

    private String path = "";
    
    public DefaultViewStorage(String configPath) {
        
        path = configPath;
        File f = new File(path);
        if (!f.exists()) {
            f.mkdirs();
        }
        path = path + System.getProperty("file.separator") + "currentView";
    }

    @Override
    public boolean storeView(View view) {
        if (!view.equals(readView())) {
            File f = new File(path);
            try {
                DataOutputStream dos = new DataOutputStream(new FileOutputStream(f));
                StateCodecs.writeView(view, dos);
                dos.flush();
                dos.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public View readView() {
        File f = new File(path);
        if (!f.exists()) {
            return null;
        }
        try {
            DataInputStream dis = new DataInputStream(new FileInputStream(f));
            View ret = StateCodecs.readView(dis);
            dis.close();

            return ret;
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] getBytes(View view) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            StateCodecs.writeView(view, dos);
            dos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public View getView(byte[] bytes) {
        try {
            return StateCodecs.readView(new DataInputStream(new ByteArrayInputStream(bytes)));
        } catch (Exception e) {
            return null;
        }
    }
}

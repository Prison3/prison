// IPUserManagerService.aidl
package com.android.prison.system.user;

// Declare any non-default types here with import statements
import com.android.prison.system.user.PUserInfo;
import java.util.List;


interface IPUserManagerService {
    PUserInfo getUserInfo(int userId);
    boolean exists(int userId);
    PUserInfo createUser(int userId);
    List<PUserInfo> getUsers();
    void deleteUser(int userId);
}

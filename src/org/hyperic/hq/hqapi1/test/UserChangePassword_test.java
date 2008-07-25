package org.hyperic.hq.hqapi1.test;

import org.hyperic.hq.hqapi1.UserApi;
import org.hyperic.hq.hqapi1.types.User;
import org.hyperic.hq.hqapi1.types.CreateUserResponse;
import org.hyperic.hq.hqapi1.types.ChangePasswordResponse;
import org.hyperic.hq.hqapi1.types.GetUsersResponse;

public class UserChangePassword_test extends UserTestBase {

    public UserChangePassword_test(String name) {
        super(name);
    }


    public void testChangePassword() throws Exception {

        UserApi api = getUserApi();

        // Create a new user.
        User u = createTestUsers(1).get(0);

        final String NEWPASS = "NEWPASSWORD";
        // Change that users password.
        ChangePasswordResponse response = api.changePassword(u, NEWPASS);
        hqAssertSuccess(response);

        // Log in as the new user and list the users.
        UserApi api2 = getUserApi(u.getName(), NEWPASS);
        GetUsersResponse getResponse = api2.getUsers();
        hqAssertSuccess(getResponse);
    }

    public void testChangePasswordEmpty() throws Exception {

        UserApi api = getUserApi();

        // Test changing a password to an empty string.
        User u = createTestUsers(1).get(0);
        ChangePasswordResponse response = api.changePassword(u, "");
        hqAssertFailureInvalidParameters(response);        
    }

    public void testChangePasswordNull() throws Exception {
        UserApi api = getUserApi();

        // Test changing a password to a null string.
        User u = createTestUsers(1).get(0);
        ChangePasswordResponse response = api.changePassword(u, null);
        hqAssertFailureInvalidParameters(response);
    }
}

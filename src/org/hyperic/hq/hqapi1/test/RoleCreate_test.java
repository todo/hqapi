package org.hyperic.hq.hqapi1.test;

import org.hyperic.hq.hqapi1.types.Role;
import org.hyperic.hq.hqapi1.types.CreateRoleResponse;
import org.hyperic.hq.hqapi1.types.Operation;
import org.hyperic.hq.hqapi1.RoleApi;

import java.util.List;
import java.util.ArrayList;

public class RoleCreate_test extends RoleTestBase {

    public RoleCreate_test(String name) {
        super(name);
    }

    public void testRoleCreateNoOps() throws Exception {

        RoleApi api = getRoleApi();
        Role r = generateTestRole();

        CreateRoleResponse response = api.createRole(r);
        hqAssertSuccess(response);
    }

    public void testRoleCreateViewOps() throws Exception {

        RoleApi api = getRoleApi();
        Role r = generateTestRole();

        r.getOperation().addAll(VIEW_OPS);

        CreateRoleResponse response = api.createRole(r);
        hqAssertSuccess(response);

        Role role = response.getRole();
        for (Operation o : VIEW_OPS) {
            assertTrue("Created role does not contain operation " + o.value(),
                       role.getOperation().contains(o));
        }
    }

    public void testRoleCreateDuplicate() throws Exception {

        RoleApi api = getRoleApi();
        Role r = generateTestRole();

        CreateRoleResponse response = api.createRole(r);
        hqAssertSuccess(response);

        CreateRoleResponse existsResponse = api.createRole(r);
        hqAssertFailureObjectExists(existsResponse);
    }
}
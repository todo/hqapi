/*
 * NOTE: This copyright does *not* cover user programs that use Hyperic
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2004-2011], VMware, Inc.
 * This file is part of Hyperic.
 *
 * Hyperic is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.hqapi1.test;

import org.hyperic.hq.hqapi1.GroupApi;
import org.hyperic.hq.hqapi1.HQApi;
import org.hyperic.hq.hqapi1.MaintenanceApi;
import org.hyperic.hq.hqapi1.MetricApi;
import org.hyperic.hq.hqapi1.types.Alert;
import org.hyperic.hq.hqapi1.types.AlertDefinition;
import org.hyperic.hq.hqapi1.types.DataPoint;
import org.hyperic.hq.hqapi1.types.GroupsResponse;
import org.hyperic.hq.hqapi1.types.LastMetricDataResponse;
import org.hyperic.hq.hqapi1.types.MaintenanceEvent;
import org.hyperic.hq.hqapi1.types.MaintenanceResponse;
import org.hyperic.hq.hqapi1.types.MaintenanceState;
import org.hyperic.hq.hqapi1.types.Group;
import org.hyperic.hq.hqapi1.types.GroupResponse;
import org.hyperic.hq.hqapi1.types.Metric;
import org.hyperic.hq.hqapi1.types.MetricResponse;
import org.hyperic.hq.hqapi1.types.Operation;
import org.hyperic.hq.hqapi1.types.Resource;
import org.hyperic.hq.hqapi1.types.Role;
import org.hyperic.hq.hqapi1.types.StatusResponse;
import org.hyperic.hq.hqapi1.types.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MaintenanceSchedule_test extends MaintenanceTestBase {

    private static final long SECOND = 1000;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;

    public MaintenanceSchedule_test(String name) {
        super(name);
    }

    public void testScheduleInvalidGroup() throws Exception {

        MaintenanceApi mApi = getApi().getMaintenanceApi();

        long start = System.currentTimeMillis() + HOUR;
        long end = start + HOUR;
        MaintenanceResponse response = mApi.schedule(Integer.MAX_VALUE,
                                                     start, end);
        hqAssertFailureObjectNotFound(response);
    }

    public void testScheduleInvalidWindow() throws Exception {

        MaintenanceApi mApi = getApi().getMaintenanceApi();

        Group g = getFileServerMountCompatibleGroup();
        long end = System.currentTimeMillis() + HOUR;
        long start = end + HOUR;
        MaintenanceResponse response = mApi.schedule(g.getId(),
                                                     start, end);
        hqAssertFailureInvalidParameters(response);
        cleanupGroup(g);
    }

    public void testScheduleInPast() throws Exception {

        MaintenanceApi mApi = getApi().getMaintenanceApi();

        Group g = getFileServerMountCompatibleGroup();
        long start = 0;
        long end = 100;
        MaintenanceResponse response = mApi.schedule(g.getId(),
                                                     start, end);
        hqAssertFailureInvalidParameters(response);
        cleanupGroup(g);         
    }

    public void testScheduleGroup() throws Exception {

        MaintenanceApi mApi = getApi().getMaintenanceApi();

        Group g = getFileServerMountCompatibleGroup();
        long start = System.currentTimeMillis() + HOUR;
        long end = start + HOUR;
        
        MaintenanceEvent event = schedule(g, start, end);

        StatusResponse unscheduleResponse = mApi.unschedule(g.getId());
        hqAssertSuccess(unscheduleResponse);

        cleanupGroup(g);         
    }

    public void testScheduleService() throws Exception {

        MaintenanceApi mApi = getApi().getMaintenanceApi();

        List<Resource> resources = getFileServerMountResources();
        Resource service = resources.get(0);
        long start = System.currentTimeMillis() + HOUR;
        long end = start + HOUR;
        
        MaintenanceEvent event = schedule(service, start, end);

        StatusResponse unscheduleResponse = mApi.unschedule(service);
        hqAssertSuccess(unscheduleResponse);
    }
    
    public void testFireAlertsBeforeSchedulingCompatibleGroup()
        throws Exception {

        HQApi api = getApi();

        // create resource
        Resource resource = createControllableResource(api);
        
        // create group
        Group maintGroup = createGroup(Collections.singletonList(resource));

        // create classic alert definitions
        AlertDefinition alertDefFireOnce = 
            createAvailabilityAlertDefinition(resource, null, false, true, 1);
        AlertDefinition alertDefFireEveryTime = 
            createAvailabilityAlertDefinition(resource, null, false, false, 1);

        // TODO create group alert definitions
        
        // insert a fake 'up' measurement so that
        // the alert definitions will fire.
        final double AVAIL_UP = 1;
        Alert alertFireOnce = fireAvailabilityAlert(alertDefFireOnce, true, AVAIL_UP);
        Alert alertFireEveryTime = fireAvailabilityAlert(alertDefFireEveryTime, false, AVAIL_UP);
        
        // check that availability measurement is enabled before the maintenance
        Metric availMetric = findAvailabilityMetric(resource);
        assertTrue("Availability measurement is not enabled for " + resource.getName(),
                    availMetric.isEnabled());
        
        // update the availability interval to 1 minute
        setMetricInterval(availMetric, MINUTE);
        
        // check that the resource's availability is UP before the maintenance
        DataPoint lastAvail = getLastAvailability(availMetric);
        assertEquals(AVAIL_UP, lastAvail.getValue());
        
        // schedule maintenance
        long maintStart = System.currentTimeMillis() + 5*SECOND;
        long maintEnd = maintStart + MINUTE;

        MaintenanceEvent event = schedule(maintGroup, maintStart, maintEnd);

        // wait for maintenance to start
        waitForMaintenanceStateChange(maintGroup, MaintenanceState.RUNNING);
        
        // try to modify group membership while downtime is in progress
        modifyGroupMembershipDuringDowntime(maintGroup);
        
        // validate alert definitions during the maintenance
        // the internal enabled flag should be false for all alert definitions
        alertDefFireOnce = getAlertDefinition(alertDefFireOnce.getId());
        validateAvailabilityAlertDefinition(alertDefFireOnce, true, false);
        
        alertDefFireEveryTime = getAlertDefinition(alertDefFireEveryTime.getId());
        validateAvailabilityAlertDefinition(alertDefFireEveryTime, false, false);
        
        // check that availability measurement is disabled during the maintenance      
        availMetric = findAvailabilityMetric(resource, false);
        assertFalse("Availability measurement is enabled for " + resource.getName(),
                    availMetric.isEnabled());
        
        // check that the resource's availability is in a PAUSED state
        // during the maintenance
        final double AVAIL_PAUSED = -0.01;
        // TODO: there's been some intermittent timing issues where HQ does not save
        // availability data fast enough for the test. pausing testing as a workaround.
        pauseTest(2*SECOND);
        lastAvail = getLastAvailability(availMetric);
        assertEquals(AVAIL_PAUSED, lastAvail.getValue());
        
        // wait for maintenance to end
        waitForMaintenanceStateChange(maintGroup, MaintenanceState.COMPLETE);
        
        // validate alert definitions after the maintenance
        // the internal enabled flag should still be false for the willRecover
        // alert definition that fired before the maintenance
        alertDefFireOnce = getAlertDefinition(alertDefFireOnce.getId());
        validateAvailabilityAlertDefinition(alertDefFireOnce, true, false);
        
        // the internal enabled flag should be true after the maintenance for
        // the alert definition that is configured to fire every time
        alertDefFireEveryTime = getAlertDefinition(alertDefFireEveryTime.getId());
        validateAvailabilityAlertDefinition(alertDefFireEveryTime, false, true);
        
        // check that availability measurement is enabled after the maintenance
        availMetric = findAvailabilityMetric(resource);
        assertTrue("Availability measurement is not re-enabled for " + resource.getName(),
                    availMetric.isEnabled());
        
        // need to check that the resources's availability has recovered from a pause state
        long timeout = 10*MINUTE;
        long now = System.currentTimeMillis();
        
        while (System.currentTimeMillis() < (now + (timeout))) {
            lastAvail = getLastAvailability(availMetric);
                        
            if (lastAvail.getValue() == AVAIL_UP) {
                break;
            } else {
                // if it's not up, it should still be paused
                assertEquals(AVAIL_PAUSED, lastAvail.getValue());
                
                lastAvail = null;
            }
            Thread.sleep(30*SECOND);
        }
        
        assertNotNull("The resource's availability did not recover from a pause state",
                      lastAvail);
        
        cleanupGroup(maintGroup, true);         
    }
    
    /**
     * To validate HQ-2038
     */
    private void modifyGroupMembershipDuringDowntime(Group g) 
        throws Exception {
        
        GroupApi groupApi = getApi().getGroupApi();
        
        List<Resource> existingResources = new ArrayList<Resource>();
        existingResources.addAll(g.getResource());
        assertTrue(existingResources.size() > 0);

        // remove resources from group
        g.getResource().clear();

        GroupsResponse syncResponse = groupApi.syncGroups(Collections.singletonList(g));
        hqAssertFailureOperationDenied(syncResponse);
        
        // reset by adding resources back to group
        g.getResource().addAll(existingResources);
        assertTrue(g.getResource().size() > 0);
    }
    
    public void testScheduleNoGroupPermission() throws Exception {
        
        List<User> users = createTestUsers(1);
        User user = users.get(0);
        
        HQApi apiUnpriv = getApi(user.getName(), TESTUSER_PASSWORD);
        MaintenanceApi mApi = apiUnpriv.getMaintenanceApi();

        Group g = getFileServerMountCompatibleGroup();
        long start = System.currentTimeMillis() + HOUR;
        long end = start + HOUR;

        MaintenanceResponse response = mApi.schedule(g.getId(),
                                                     start, end);

        hqAssertFailurePermissionDenied(response);

        deleteTestUsers(users);
        cleanupGroup(g);         
    }
    
    /**
     * To validate HQ-1832
     */
    public void testScheduleNoMaintenancePermission() throws Exception {
        
        // create user
        List<User> users = createTestUsers(1);
        User user = users.get(0);

        // create role with view group permissions
        Role viewRole = createRole(Collections.singletonList(user),
                                   Collections.singletonList(Operation.VIEW_RESOURCE_GROUP));
        
        // create group with view role
        Group g = getFileServerMountCompatibleGroup();
        g.getRole().add(viewRole);
        GroupResponse groupResponse = getApi().getGroupApi().updateGroup(g);
        hqAssertSuccess(groupResponse);
        Group groupWithRole = groupResponse.getGroup();
        assertEquals("The group should have one role",
                     1, groupWithRole.getRole().size());

        // schedule maintanence with insufficient permissions
        HQApi apiUnpriv = getApi(user.getName(), TESTUSER_PASSWORD);
        MaintenanceApi mApi = apiUnpriv.getMaintenanceApi();

        long start = System.currentTimeMillis() + HOUR;
        long end = start + HOUR;

        MaintenanceResponse maintResponse = 
            mApi.schedule(groupWithRole.getId(), start, end);

        hqAssertFailurePermissionDenied(maintResponse);

        // cleanup
        deleteTestUsers(users);
        cleanupRole(viewRole);
        cleanupGroup(groupWithRole);
    }

    private void setMetricInterval(Metric metric, long interval) 
        throws Exception {
        
        MetricApi metricApi = getApi().getMetricApi();
        
        metric.setInterval(interval);

        List<Metric> syncMetrics = new ArrayList<Metric>();
        syncMetrics.add(metric);
        StatusResponse syncResponse = metricApi.syncMetrics(syncMetrics);
        hqAssertSuccess(syncResponse);

        MetricResponse metricResponse = metricApi.getMetric(metric.getId());
        hqAssertSuccess(metricResponse);
        
        assertEquals("Interval for metric " + metric.getName() + " not updated",
                     interval, metricResponse.getMetric().getInterval());
    }
    
    private DataPoint getLastAvailability(Metric availMetric) 
        throws Exception {
        
        LastMetricDataResponse lastAvailResponse = 
            getApi().getMetricDataApi().getData(availMetric);
        hqAssertSuccess(lastAvailResponse);
        assertNotNull(lastAvailResponse.getLastMetricData());
        DataPoint lastAvail = lastAvailResponse.getLastMetricData().getDataPoint();
        assertNotNull(lastAvail);
        
        return lastAvail;
    }
    
    private void waitForMaintenanceStateChange(Group g, MaintenanceState newState) 
        throws Exception{
    
        MaintenanceEvent event = get(g);
        assertNotNull("The group must have a scheduled maintenance event",
                       event);
        MaintenanceState initialState = event.getState();
        MaintenanceState currentState = event.getState();
        
        long timeout = 0;
        if (newState.value().equals(MaintenanceState.RUNNING.value())) {
            timeout = event.getStartTime() + 30*SECOND;
        } else if (newState.value().equals(MaintenanceState.COMPLETE.value())) {
            timeout = event.getEndTime() + 30*SECOND;
        }
        
        while (!currentState.value().equals(newState.value())) {
            if (System.currentTimeMillis() >= timeout) {
                String message = "The maintenance event did not change state from "
                                    + initialState.value() + " to " 
                                    + newState.value() + " in time.";
                throw new Exception(message);
            }
            
            pauseTest(5*SECOND);
            
            event = get(g);
            
            if (event == null) {
                currentState = MaintenanceState.COMPLETE;
            } else {
                currentState = get(g).getState();
            }
        }
    }
}

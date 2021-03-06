package jenkins.plugins.rancher;


import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Strings;
import hudson.*;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.rancher.action.InServiceStrategy;
import jenkins.plugins.rancher.action.ServiceUpgrade;
import jenkins.plugins.rancher.entity.*;
import jenkins.plugins.rancher.entity.Stack;
import jenkins.plugins.rancher.util.CredentialsUtil;
import jenkins.plugins.rancher.util.Parser;
import jenkins.plugins.rancher.util.ServiceField;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class RancherBuilder extends Builder implements SimpleBuildStep {

    public static final String UPGRADED = "upgraded";
    public static final String ACTIVE = "active";
    public static final String INACTIVE = "inactive";
    public static final int DEFAULT_TIMEOUT = 50;

    private final String environmentId;
    private final String endpoint;
    private final String credentialId;

    private final String service;
    private final String image;
    private final boolean confirm;
    private final String ports;
    private final String environments;
    private int timeout = 50;
    private RancherClient rancherClient;
    private CredentialsUtil credentialsUtil;
    private final String volumes;
    private final String volumeDriver;

    @DataBoundConstructor
    public RancherBuilder(
            String environmentId, String endpoint, String credentialId, String service,
            String image, boolean confirm, String ports, String environments, int timeout, String volumes, String volumeDriver) {
        this.environmentId = environmentId;
        this.endpoint = endpoint;
        this.credentialId = credentialId;
        this.service = service;
        this.image = image;
        this.confirm = confirm;
        this.ports = ports;
        this.environments = environments;
        this.timeout = timeout;
        this.volumes = volumes;
        this.volumeDriver = volumeDriver;
    }

    protected static RancherBuilder newInstance(String environmentId, String endpoint, String credentialId, String service,
                                                String image, boolean confirm, String ports, String environments, int timeout,
                                                RancherClient rancherClient, CredentialsUtil credentialsUtil) {
        RancherBuilder rancherBuilder = new RancherBuilder(environmentId, endpoint, credentialId, service, image, confirm, ports, environments, timeout, "", null);
        rancherBuilder.setCredentialsUtil(credentialsUtil);
        rancherBuilder.setRancherClient(rancherClient);
        return rancherBuilder;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        initializeClient();
        Map<String, String> buildEnvironments = getBuildEnvs(build, listener);
        String dockerUUID = String.format("docker:%s", Parser.paraser(image, buildEnvironments));
        Map<String, Object> environments = this.customEnvironments(Parser.paraser(this.environments, buildEnvironments));
        String service = Parser.paraser(this.service, buildEnvironments);
        ServiceField serviceField = new ServiceField(service);

        listener.getLogger().printf("Deploy/Upgrade image[%s] to service [%s] to rancher environment [%s/projects/%s]%n", dockerUUID, service, endpoint, getEnvironmentId());

        Stack stack = getStack(listener, serviceField, rancherClient);
        Optional<Services> services = rancherClient.services(stack.getId());
        if (!services.isPresent()) {
            throw new AbortException("Error happen when fetch stack<" + stack.getName() + "> services");
        }


        Optional<Service> serviceInstance = services.get().getData().stream().filter(service1 -> service1.getName().equals(serviceField.getServiceName())).findAny();
        if (serviceInstance.isPresent()) {
            upgradeService(serviceInstance.get(), dockerUUID, listener, environments);
        } else {
            createService(stack, serviceField.getServiceName(), dockerUUID, listener, environments);
        }
    }


    public void setCredentialsUtil(CredentialsUtil credentialsUtil) {
        this.credentialsUtil = credentialsUtil;
    }

    public void setRancherClient(RancherClient rancherClient) {
        this.rancherClient = rancherClient;
    }

    private void initializeClient() {
        if (credentialsUtil == null) {
            credentialsUtil = new CredentialsUtil();
        }

        if (rancherClient == null) {
            rancherClient = newRancherClient();
        }
    }

    private RancherClient newRancherClient() {
        Optional<StandardUsernamePasswordCredentials> credential = credentialsUtil.getCredential(credentialId);
        if (credential.isPresent()) {
            return new RancherClient(endpoint, credential.get().getUsername(), credential.get().getPassword().getPlainText());
        } else {
            return new RancherClient(endpoint);
        }
    }

    private void checkServiceState(Service service, TaskListener listener) throws AbortException {
        String state = service.getState();
        listener.getLogger().printf("service %s current state is %s%n", service.getName(), state);
        if (!(INACTIVE.equalsIgnoreCase(state) || ACTIVE.equalsIgnoreCase(state))) {
            throw new AbortException("Before upgrade service the service instance state should be 'inactive' or 'active'");
        }
    }

    private void upgradeService(Service service, String dockerUUID, TaskListener listener, Map<String, Object> environments) throws IOException {
        listener.getLogger().println("Upgrading service instance");
        checkServiceState(service, listener);
        ServiceUpgrade serviceUpgrade = new ServiceUpgrade();
        InServiceStrategy inServiceStrategy = new InServiceStrategy();

        LaunchConfig launchConfig = service.getLaunchConfig();
        launchConfig.setImageUuid(dockerUUID);
        launchConfig.getEnvironment().putAll(environments);

        if (!Strings.isNullOrEmpty(ports)) {
            launchConfig.setPorts(Arrays.asList(ports.split(",")));
            inServiceStrategy.setStartFirst(false);
        }

        //todo
        //loadVolume(launchConfig);

        inServiceStrategy.setLaunchConfig(launchConfig);
        serviceUpgrade.setInServiceStrategy(inServiceStrategy);
        Optional<Service> serviceInstance = rancherClient.upgradeService(getEnvironmentId(), service.getId(), serviceUpgrade);
        if (!serviceInstance.isPresent()) {
            throw new AbortException("upgrade service error");
        }

        waitUntilServiceStateIs(serviceInstance.get().getId(), UPGRADED, listener);

        if (!confirm) {
            return;
        }
        listener.getLogger().println("自动完成升级.......");
        rancherClient.finishUpgradeService(environmentId, serviceInstance.get().getId());
        waitUntilServiceStateIs(serviceInstance.get().getId(), ACTIVE, listener);
    }

    private void createService(Stack stack, String serviceName, String dockerUUID, TaskListener listener, Map<String, Object> environments) throws IOException {
        listener.getLogger().println("Creating service instance");
        Service service = new Service();
        service.setName(serviceName);
        LaunchConfig launchConfig = new LaunchConfig();
        launchConfig.setImageUuid(dockerUUID);
        launchConfig.setEnvironment(environments);
        if (!Strings.isNullOrEmpty(ports)) {
            launchConfig.setPorts(Arrays.asList(ports.split(",")));
        }

        loadVolume(launchConfig, stack.getId(), listener);

        service.setLaunchConfig(launchConfig);
        Optional<Service> serviceInstance = rancherClient.createService(service, getEnvironmentId(), stack.getId());

        if (!serviceInstance.isPresent()) {
            throw new AbortException("upgrade service error");
        }

        waitUntilServiceStateIs(serviceInstance.get().getId(), ACTIVE, listener);
    }


    protected void loadVolume(LaunchConfig launchConfig, String stackId, TaskListener listener) throws IOException {

        if (Strings.isNullOrEmpty(volumes)) {
            listener.getLogger().println("Not need create volumes skip." );
            return;
        }
        launchConfig.setDataVolumes(Arrays.asList(volumes.split(",")));

        if(Strings.isNullOrEmpty(volumeDriver)){
            listener.getLogger().println("No volumeDriver, Not need create volumes skip." );
            return;
        }

        Optional<StorageDrivers> drivers = rancherClient.storageDrivers(getEnvironmentId());

        Optional<StorageDriver> driver = Optional.empty();
        if (drivers.isPresent()) {
            driver = drivers.get().getData().stream().filter(d -> d.getName().equals(volumeDriver)).findAny();
        }
        if (!driver.isPresent()) {
            throw new AbortException("Not find volume dirver");
        }

        launchConfig.setVolumeDriver(volumeDriver);

        Optional<Volumes> volumes = rancherClient.volumes(getEnvironmentId(), stackId);

        String driverId = driver.get().getId();

        launchConfig.getDataVolumes().stream().map(v -> v.split(":")[0]).filter(v -> {
            Optional<Volume> volume = volumes.get().getData().stream().filter(s -> s.getName().equals(v)).findAny();
            listener.getLogger().println("check volume("+v+") exist?" + volume.isPresent());
            return !volume.isPresent();
        }).forEach((String newV) -> {
            Volume volume = new Volume();
            volume.setStackId(stackId);
            volume.setStorageDriverId(driverId);
            volume.setName(newV);
            try {
                listener.getLogger().println("create volume :" + newV);
                rancherClient.createVolume(environmentId, stackId, volume);
                listener.getLogger().println("create volume ("+newV+") success!");
            } catch (IOException e) {

            }
        });




    }


    private void waitUntilServiceStateIs(String serviceId, String targetState, TaskListener listener) throws AbortException {
        int timeoutMs = 1000 * timeout;
        long start = System.currentTimeMillis();
        long current = System.currentTimeMillis();
        listener.getLogger().println("waiting service state to be " + targetState + " (timeout:" + timeout + "s)");
        try {
            boolean success = false;
            while ((current - start) < timeoutMs) {
                Optional<Service> checkService = rancherClient.service(serviceId);
                String state = checkService.get().getState();
                if (state.equalsIgnoreCase(targetState)) {
                    listener.getLogger().println("current service state is " + targetState);
                    success = true;
                    break;
                }
                Thread.sleep(2000);
                current = System.currentTimeMillis();
            }
            if (!success) {
                throw new AbortException("timeout");
            }
        } catch (Exception e) {
            throw new AbortException("Exception happened to wait service state with message:" + e.getMessage());
        }
    }

    private Stack getStack(@Nonnull TaskListener listener, ServiceField serviceField, RancherClient rancherClient) throws IOException {
        Optional<Stacks> stacks = rancherClient.stacks(getEnvironmentId());
        if (!stacks.isPresent()) {
            throw new AbortException("error happen when fetch stack in environment<" + getEnvironmentId() + ">");
        }

        Optional<Stack> stack = stacks.get().getData().stream().filter(stackItem -> isEqual(serviceField, stackItem)).findAny();
        if (stack.isPresent()) {
            listener.getLogger().println("Stack already exist. skip");
            return stack.get();
        } else {
            listener.getLogger().println("Stack not exist, create first");
            return createStack(serviceField, rancherClient);
        }
    }

    private Stack createStack(ServiceField serviceField, RancherClient rancherClient) throws IOException {
        Stack stack = new Stack();
        stack.setName(serviceField.getStackName());
        Optional<Stack> stackOptional = rancherClient.createStack(stack, getEnvironmentId());
        if (!stackOptional.isPresent()) {
            throw new AbortException("error happen when create stack");
        } else {
            return stackOptional.get();
        }
    }

    private Map<String, Object> customEnvironments(String environments) {
        HashMap<String, Object> map = new HashMap<>();
        String[] fragments = environments.split(",");
        for (String fragement : fragments) {
            if (fragement.contains(":")) {
                String[] env = fragement.split(":", 2);
                map.put(env[0], env[1]);
            }
        }
        return map;
    }

    private boolean isEqual(ServiceField serviceField, Stack stack1) {
        return stack1.getName().equals(serviceField.getStackName());
    }

    private Map<String, String> getBuildEnvs(Run<?, ?> build, TaskListener listener) {
        Map<String, String> envs = new HashMap<>();
        try {
            EnvVars environment = build.getEnvironment(listener);
            environment.keySet().forEach(key -> {
                String value = environment.get(key);
                envs.put(key, value);
            });
        } catch (Exception e) {
            listener.getLogger().println(e.getMessage());
        }
        return envs;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public boolean isConfirm() {
        return confirm;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getEnvironments() {
        return environments;
    }

    public String getImage() {
        return image;
    }

    public String getPorts() {
        return ports;
    }

    public String getService() {
        return service;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public int getTimeout() {
        return timeout == 0 ? DEFAULT_TIMEOUT : timeout;
    }

    public String getVolumes() {
        return volumes;
    }

    public String getVolumeDriver() {
        return volumeDriver;
    }

    @Symbol("rancher")
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private static final CredentialsUtil credentialsUtil = new CredentialsUtil();

        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        public String getDisplayName() {
            return "Deploy/Upgrade Rancher Service";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

        public ListBoxModel doFillCredentialIdItems() {
            if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) {
                return new ListBoxModel();
            }
            List<StandardUsernamePasswordCredentials> credentials = credentialsUtil.getCredentials();
            return new StandardUsernameListBoxModel()
                    .withEmptySelection()
                    .withAll(credentials);
        }

        public FormValidation doTestConnection(
                @QueryParameter("endpoint") final String endpoint,
                @QueryParameter("environmentId") final String environmentId,
                @QueryParameter("credentialId") final String credentialId
        ) throws IOException, ServletException {

            try {
                RancherClient client;
                Optional<StandardUsernamePasswordCredentials> credential = credentialsUtil.getCredential(credentialId);
                if (credential.isPresent()) {
                    client = new RancherClient(endpoint, credential.get().getUsername(), credential.get().getPassword().getPlainText());
                } else {
                    client = new RancherClient(endpoint);
                }
                Optional<Environment> environment = client.environment(environmentId);
                if (!environment.isPresent()) {
                    return FormValidation.error("Environment [" + environmentId + "] not found please check configuration");
                }
                return FormValidation.ok("Connection Success");
            } catch (Exception e) {
                return FormValidation.error("Connection fails with message : " + e.getMessage());
            }
        }

        public FormValidation doCheckTimeout(@QueryParameter int value) {
            return value > 0 ? FormValidation.ok() : FormValidation.error("Time should be at least 1");
        }

        public FormValidation doCheckPorts(@QueryParameter String value) {
            String[] ports = value.split(",");
            boolean inValid = Arrays.asList(ports)
                    .stream()
                    .anyMatch(
                            port -> Arrays.asList(port.split(":"))
                                    .stream()
                                    .anyMatch(part -> !StringUtils.isNumeric(part)));
            return inValid ? FormValidation.error("Ports config should be like: 8080:8080,8181:8181") : FormValidation.ok();
        }

        public FormValidation doCheckCredentialId(@QueryParameter String value) {
            return !Strings.isNullOrEmpty(value)
                    && credentialsUtil.getCredential(value).isPresent()
                    ? FormValidation.ok() : FormValidation.warning("API key is required when Rancher ACL is enable");
        }

        public FormValidation doCheckEndpoint(@QueryParameter String value) {
            try {
                new URL(value);
                return FormValidation.ok();
            } catch (MalformedURLException e) {
                return FormValidation.error("Not a rancher v2 api endpoint");
            }
        }

        public FormValidation doCheckAccessKey(@QueryParameter String value) {
            return !Strings.isNullOrEmpty(value) ? FormValidation.ok() : FormValidation.error("AccessKey can't be empty");
        }

        public FormValidation doCheckSecretKey(@QueryParameter String value) {
            return !Strings.isNullOrEmpty(value) ? FormValidation.ok() : FormValidation.error("SecretKey can't be empty");
        }

        public FormValidation doCheckEnvironmentId(@QueryParameter String value) {
            return !Strings.isNullOrEmpty(value) ? FormValidation.ok() : FormValidation.error("EnvironmentId can't be empty");
        }

        public FormValidation doCheckService(@QueryParameter String value) {
            boolean validate = !Strings.isNullOrEmpty(value) && value.contains("/") && value.split("/").length == 2;
            return validate ? FormValidation.ok() : FormValidation.error("Service name should be like stack/service");
        }

        public FormValidation doCheckImage(@QueryParameter String value) {
            return !Strings.isNullOrEmpty(value) ? FormValidation.ok() : FormValidation.error("Docker image can't be empty");
        }

    }

}

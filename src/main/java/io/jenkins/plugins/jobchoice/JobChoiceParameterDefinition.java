package io.jenkins.plugins.jobchoice;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.*;
import hudson.model.*;
import java.util.*;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.springframework.security.access.AccessDeniedException;

public class JobChoiceParameterDefinition extends SimpleParameterDefinition {

    private Map<String, String> jobs;
    private ArrayList<String> choices;

    private final String path;

    private long lastUpdate = 0;

    @DataBoundConstructor
    public JobChoiceParameterDefinition(@NonNull final String name, final String path) {
        super(name);
        this.path = path;
        this.updateData();
    }

    @Override
    public ParameterValue createValue(String value) {
        return createParameterValue(value);
    }

    @Override
    public ParameterValue createValue(@NonNull StaplerRequest req, JSONObject jo) {
        final StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);

        return createParameterValue(value.getValue());
    }

    protected ParameterValue createParameterValue(final String value) throws IllegalArgumentException {
        final String outPutValue = jobs.get(value);
        if (outPutValue == null) {
            throw new IllegalArgumentException(Messages.JobChoiceParameterDefinition_IllegalChoice(value, getName()));
        }

        return new StringParameterValue(getName(), Util.fixNull(outPutValue), getDescription());
    }

    private String defaultValue;

    public String getDefaultValue() {
        return this.defaultValue;
    }

    @DataBoundSetter
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    private String description;

    public String getDescription() {
        return this.description;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return choices
     */
    @Exported
    public List<String> getChoices() {
        // update data to make sure newest data is used
        this.updateData();

        return this.choices;
    }

    private void updateData() throws IllegalArgumentException, AccessDeniedException {
        long now = System.currentTimeMillis();
        // if last update is not older than 1min ago do no update.
        if (now - lastUpdate < 60000) {
            return;
        }
        lastUpdate = now;

        ItemGroup group = getItemGroupByPath(this.path);
        if (group == null) {
            throw new IllegalArgumentException(Messages.JobChoiceParameterDefinition_IllegalPath(this.path, getName()));
        }

        HashMap<String, String> jobMap = new HashMap<>();
        HashMap<String, Calendar> timeMap = new HashMap<>();
        ArrayList<String> choices = new ArrayList<>();

        for (Object ob : group.getAllItems()) {
            if (!(ob instanceof Item)) {
                continue;
            }
            Item it = (Item) ob;
            jobMap.put(it.getName(), it.getFullName());
            choices.add(it.getName());
            if (ob instanceof Job) {
                Job j = (Job) ob;
                if (j.getLastBuild() != null) {
                    timeMap.put(it.getName(), j.getLastBuild().getTimestamp());
                }
            }
        }
        this.jobs = jobMap;

        choices.sort((d1, d2) -> {
            Calendar c1 = timeMap.get(d1);
            Calendar c2 = timeMap.get(d2);
            return c1.compareTo(c2);
        });

        this.choices = choices;
    }

    private ItemGroup getItemGroupByPath(@NonNull String fullName) throws AccessDeniedException {
        StringTokenizer tokens = new StringTokenizer(fullName, "/");
        ItemGroup parent = Jenkins.getInstanceOrNull();
        if (!tokens.hasMoreTokens()) {
            return null;
        } else {
            while (parent != null) {
                Item item = parent.getItem(tokens.nextToken());
                if (!tokens.hasMoreTokens()) {
                    if (item instanceof ItemGroup) {
                        return (ItemGroup) item;
                    }

                    return null;
                }

                if (!(item instanceof ItemGroup)) {
                    return null;
                }

                if (!item.hasPermission(Item.READ)) {
                    return null;
                }

                parent = (ItemGroup) item;
            }
        }

        return null;
    }

    @Extension
    @Symbol("jobChoice")
    public static final class DescriptorImpl extends ParameterDescriptor {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.JobChoiceParameterDefinition_DisplayName();
        }
    }
}

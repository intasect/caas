/**
 * Copyright 2008, 2009 Mark Hooijkaas This file is part of the Caas tool. The Caas tool is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version
 * 3 of the License, or (at your option) any later version. The Caas tool is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details. You should have received a copy of the GNU General Public License along with the Caas
 * tool. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kisst.cordys.caas.main;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.kisst.cordys.caas.Caas;
import org.kisst.cordys.caas.CordysSystem;
import org.kisst.cordys.caas.Organization;
import org.kisst.cordys.caas.User;
import org.kisst.cordys.caas.cm.CaasPackage;
import org.kisst.cordys.caas.cm.CcmFilesObjective;
import org.kisst.cordys.caas.cm.Objective;
import org.kisst.cordys.caas.cm.Template;
import org.kisst.cordys.caas.cm.Template.ETemplateOption;
import org.kisst.cordys.caas.cm.gui.CcmGui;
import org.kisst.cordys.caas.util.FileUtil;

/**
 * This class holds the ConfigurationManager command. It contains the different sub commands of the configuration manager.
 */
public class CmCommand extends CompositeCommand
{
    /**
     * The base host command class. It parses the command's options.
     */
    private abstract class HostCommand extends CommandBase
    {
        /** Holds the command line interface to use */
        protected final Cli cli = new Cli();
        /** Holds the option that specifies the system that we should connect to. */
        protected final Cli.StringOption systemOption = cli.stringOption("s", "system", "the system to use", null);
        /** Holds the name of the organization to connect to */
        protected final Cli.StringOption orgOption = cli.stringOption("o", "organization", "the organization to use", null);

        /**
         * Instantiates a new host command.
         * 
         * @param usage The usage
         * @param summary The summary
         */
        public HostCommand(String usage, String summary)
        {
            super(usage, summary);
        }

        protected CordysSystem getSystem()
        {
            return Caas.getSystem(Caas.defaultSystem);
        }

        protected Organization getOrg(String defaultOrg)
        {
            if (orgOption.isSet())
                return getSystem().org.getByName(orgOption.get());
            else
                return getSystem().org.getByName(defaultOrg);
        }

        protected String[] checkArgs(String[] args)
        {
            args = cli.parse(args);
            if (systemOption.isSet())
                Caas.defaultSystem = systemOption.get();
            return args;
        }

        @Override
        public String getHelp()
        {
            return "\nOPTIONS\n" + cli.getSyntax("\t");
        }
    }

    private ArrayList<String> getFiles(String[] args)
    {
        ArrayList<String> result = new ArrayList<String>();
        for (String path : args)
        {
            File f = new File(path);
            if (f.isFile())
                result.add(path);
            else if (f.isDirectory())
            {
                for (String p2 : f.list())
                {
                    File f2 = new File(f, p2);
                    if (f2.isFile())
                        result.add(f2.getPath());
                }
            }
            else
                throw new RuntimeException("Unknown path " + path);
        }
        return result;
    }

    private TextUi ui = new TextUi();

    private Command check = new HostCommand("[options] <ccm file>|<dir> ...", "validates the given install info") {
        @Override
        public void run(String[] args)
        {
            args = checkArgs(args);
            Organization org = null;
            for (String path : getFiles(args))
            {
                CaasPackage p = new CaasPackage(path, getSystem(), orgOption.get());
                if (org == null || !org.getName().equals(p.getDefaultOrgName()))
                    org = getOrg(p.getDefaultOrgName());
                boolean result = p.check(ui) == 0;
                Environment.debug(path + "\t" + result);
            }
        }
    };

    private Command gui = new HostCommand("[options] <ccm file>|<dir> ...", "shows gui for the given install info") {
        @Override
        public void run(String[] args)
        {
            args = checkArgs(args);
            Objective target = new CcmFilesObjective(args, getSystem());
            CcmGui gui = new CcmGui(target);
            gui.run();
        }
    };

    private Command configure = new HostCommand("[options] <ccm file>|<dir> ...", "installs the given isvp") {
        @Override
        public void run(String[] args)
        {
            args = checkArgs(args);
            Organization org = null;
            for (String path : getFiles(args))
            {
                CaasPackage p = new CaasPackage(path, getSystem(), orgOption.get());
                if (org == null || !org.getName().equals(p.getDefaultOrgName()))
                    org = getOrg(p.getDefaultOrgName());
                p.configure(ui);
            }
        }
    };
    private Command purge = new HostCommand("[options] <ccm file>|<dir> ...", "removes the given isvp") {
        @Override
        public void run(String[] args)
        {
            args = checkArgs(args);
            Organization org = null;
            for (String path : getFiles(args))
            {
                CaasPackage p = new CaasPackage(path, getSystem(), orgOption.get());
                if (org == null || !org.getName().equals(p.getDefaultOrgName()))
                    org = getOrg(p.getDefaultOrgName());
                p.purge(ui);
            }
        }
    };
    private Command deductUserCcmFiles = new HostCommand("[options]",
            "create a ccm files for each user of the given organization") {
        @Override
        public void run(String[] args)
        {
            args = checkArgs(args);
            Organization org = getOrg(null);
            for (User u : org.users)
            {
                String filename = "user-" + u.getName() + ".ccm";
                File f = new File(filename);
                if (f.exists())
                {
                    Environment.debug("skipping file " + filename + ", already exists");
                    continue;
                }
                Template tpl = new Template(org, null, null, u);

                Properties props = getSystem().getProperties();
                @SuppressWarnings({ "rawtypes", "unchecked" })
                Map<String, String> variables = new HashMap<String, String>((Map) props);
                tpl.save(filename, variables);
            }
        }
    };

    private Command deductIsvpCcmFiles = new HostCommand("[options]",
            "create a ccm files for each isvp of the given organization") {
        @Override
        public void run(String[] args)
        {
            args = checkArgs(args);
            Organization org = getOrg(null);
            for (org.kisst.cordys.caas.Package isvp : org.getSystem().packages)
            {
                String filename = "isvp-" + isvp.getName() + ".ccm";
                File f = new File(filename);
                if (f.exists())
                {
                    Environment.debug("skipping file " + filename + ", already exists");
                    continue;
                }
                Template tpl = new Template(org, null, isvp, null);
                if (!tpl.isEmpty())
                {
                    Properties props = getSystem().getProperties();
                    @SuppressWarnings({ "rawtypes", "unchecked" })
                    Map<String, String> variables = new HashMap<String, String>((Map) props);
                    tpl.save(filename, variables);
                }
            }
        }
    };

    /**
     * This command will create the template based on the configured system and organization.
     */
    private Command template = new TemplateHostCommand("[options] <template file>",
            "create a template based on the given organization") {

        private final Cli.StringOption isvpName = cli.stringOption("i", "isvpName", "the isvpName to use for custom content",
                null);

        /**
         * @see org.kisst.cordys.caas.main.CommandBase#run(java.lang.String[])
         */
        @Override
        public void run(String[] args)
        {
            args = checkArgs(args);

            // Create the template for the configured organization
            String orgz = System.getProperty("template.org");
            Organization organization = getOrg(orgz);
            Template templ = new Template(organization, isvpName.get(), getOptions());

            // Load the properties for the given organization
            Map<String, String> variables = Environment.get().loadSystemProperties(getSystem().getName(), organization.getName());

            // Save the template
            templ.save(args[0], variables);
        }
    };

    /**
     * This method will apply the template to the given system and organization.
     */
    private Command create = new TemplateHostCommand("[options] <template file>",
            "create elements in an organization based on the given template") {
        @Override
        public void run(String[] args)
        {
            args = checkArgs(args);
            Template templ = new Template(FileUtil.loadString(args[0]), getOptions());

            // Get the organization in which the template should be applied.
            String orgz = System.getProperty("create.org");
            Organization organization = getOrg(orgz);

            // Load the properties for the given organization.
            Map<String, String> map = Environment.get().loadSystemProperties(this.getSystem().getName(), organization.getName());

            // Add the organization name, system name and LDAP root to the map
            map.put("sys.org.name", organization.getName());
            map.put("sys.ldap.root", organization.getSystem().getDn());
            map.put("sys.name", this.getSystem().getName());

            // Apply the template to the given organization using the given properties.
            templ.apply(organization, map);
        }
    };

    /**
     * Instantiates a new cm command.
     * 
     * @param name The name
     */
    public CmCommand(String name)
    {
        super("caas " + name, "run a caas configuration manager command");

        commands.put("gui", gui);
        commands.put("check", check);
        commands.put("configure", configure);
        commands.put("purge", purge);
        commands.put("template", template);
        commands.put("create", create);
        commands.put("deduct-user-ccm", deductUserCcmFiles);
        commands.put("deduct-isvp-ccm", deductIsvpCcmFiles);
    }

    /**
     * This class holds the template commands that can be run.
     */
    private abstract class TemplateHostCommand extends HostCommand
    {
        /** Holds the option that allows the user to specify which types they want to process */
        protected final Cli.StringOption compOption = cli.stringOption("c", "component",
                "the components that should be processed. Valid options", null);

        /**
         * Instantiates a new template host command.
         * 
         * @param usage The usage
         * @param summary The summary
         */
        public TemplateHostCommand(String usage, String summary)
        {
            super(usage, summary);
        }

        /**
         * This method gets the options valid for the template.
         * 
         * @return The options valid for the template.
         */
        public List<ETemplateOption> getOptions()
        {
            // Build up the list of the components that should be exported.
            List<Template.ETemplateOption> options = new ArrayList<Template.ETemplateOption>();
            if (!compOption.isSet() || compOption.get().indexOf(Template.ETemplateOption.ALL.option()) > -1)
            {
                // If the option is not set OR that the all is specified we do everything
                options.add(ETemplateOption.ALL);
            }
            else
            {
                String tmp = compOption.get();
                for (ETemplateOption o : Template.ETemplateOption.values())
                {
                    if (tmp.indexOf(o.option()) > -1)
                    {
                        options.add(o);
                    }
                }
            }

            return options;
        }
    }
}
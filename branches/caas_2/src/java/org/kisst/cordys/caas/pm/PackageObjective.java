/**
 * Copyright 2008, 2009 Mark Hooijkaas This file is part of the Caas tool. The Caas tool is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version
 * 3 of the License, or (at your option) any later version. The Caas tool is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details. You should have received a copy of the GNU General Public License along with the Caas
 * tool. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kisst.cordys.caas.pm;

import java.util.LinkedList;
import java.util.List;

import org.kisst.cordys.caas.CordysSystem;
import org.kisst.cordys.caas.IDeployedPackageInfo;
import org.kisst.cordys.caas.Organization;
import org.kisst.cordys.caas.Package;
import org.kisst.cordys.caas.main.Environment;
import org.kisst.cordys.caas.util.StringUtil;
import org.kisst.cordys.caas.util.XmlNode;

/**
 * Holds the Class PackageObjective. This objective will check if a certain package is loaded on the target system.
 */
public class PackageObjective implements Objective
{

    /** Holds the name. */
    private final String name;

    /** Holds the versions. */
    private final LinkedList<Version> versions = new LinkedList<Version>();

    /**
     * Instantiates a new package objective.
     * 
     * @param node The node
     */
    public PackageObjective(XmlNode node)
    {
        name = node.getAttribute("name");
        for (XmlNode child : node.getChildren())
        {
            if ("version".equals(child.getName()))
                versions.add(new Version(child));
            else
                throw new RuntimeException("Unknown element in isvp section " + name + ":\n" + child.getPretty());
        }
    }

    /**
     * @see org.kisst.cordys.caas.pm.Objective#check(org.kisst.cordys.caas.Organization)
     */
    public boolean check(Organization org)
    {
        CordysSystem system = org.getSystem();
        Environment env = Environment.get();

        Package pkg = system.packages.getByName(name);
        if (pkg == null)
        {
            env.error("Required package " + name + " is not installed");
            return false;
        }

        // Get the information of the deployed package
        IDeployedPackageInfo dpi = pkg.getInfo();

        // TODO: check if loaded on all necessary machines
        boolean foundMatchingVersion = false;
        for (Version v : versions)
        {
            env.debug("Checking " + pkg.getCn() + " against version " + v.getVersion());

            if (v.matches(dpi.getFullVersion()))
            {
                env.info(v.getVersion() + " matches actual version " + dpi.getFullVersion());

                foundMatchingVersion = true;
                if ("OK".equals(v.getTested()))
                    return true;
                else
                {
                    env.error("Required package " + pkg.getVarName() + " has version " + dpi.getFullVersion() + " that tested "
                            + v.getTested());
                    for (String s : v.getWarnings())
                    {
                        env.warn("\t" + s);
                    }
                }
            }
        }

        if (!foundMatchingVersion)
            env.error("Required package " + pkg.getVarName() + ", has version " + dpi.getFullVersion()
                    + " that was not mentioned in the known versions");
        return false;
    }

    /**
     * @see org.kisst.cordys.caas.pm.Objective#configure(org.kisst.cordys.caas.Organization)
     */
    public void configure(Organization org)
    {
        /* do nothing, automatically loading not supported */
    }

    /**
     * @see org.kisst.cordys.caas.pm.Objective#remove(org.kisst.cordys.caas.Organization)
     */
    public void remove(Organization org)
    {
        /* do nothing, automatically unloading not supported */
    }

    /**
     * This class holds the details of the version of the package that should be loaded.
     */
    public static class Version
    {
        /** Holds the regular expression the version should match to be 'OK'. */
        private String m_version;
        /** Holds the warning messages that should be displayed in case the package doe not match. */
        private String[] m_warnings;
        /**
         * Holds the test result for this version. When set to 'OK' it means that this is a good version. If the tested is
         * something else then it is assumed to be a 'bad' version.
         */
        private String m_tested;

        /**
         * Instantiates a new version.
         * 
         * @param node The node
         */
        public Version(XmlNode node)
        {
            m_version = node.getAttribute("version");
            m_tested = node.getAttribute("tested");

            List<XmlNode> children = node.getChildren();
            m_warnings = new String[children.size()];
            int i = 0;
            for (XmlNode child : children)
                m_warnings[i] = child.getAttribute("message");
        }

        /**
         * This method checks if the passed on version matches the defined version regex.
         * 
         * @param actualVersion The actual version that should be checked.
         * @return true if the version matches. Otherwise false.
         */
        public boolean matches(String actualVersion)
        {
            boolean retVal = false;

            if (!StringUtil.isEmptyOrNull(actualVersion))
            {
                retVal = actualVersion.matches(m_version);
            }

            return retVal;
        }

        /**
         * This method gets the test result for this version. When set to 'OK' it means that this is a good version. If the tested
         * is something else then it is assumed to be a 'bad' version.
         * 
         * @return The test result for this version. When set to 'OK' it means that this is a good version. If the tested is
         *         something else then it is assumed to be a 'bad' version.
         */
        public String getTested()
        {
            return m_tested;
        }

        /**
         * This method sets the test result for this version. When set to 'OK' it means that this is a good version. If the tested
         * is something else then it is assumed to be a 'bad' version.
         * 
         * @param tested The test result for this version. When set to 'OK' it means that this is a good version. If the tested is
         *            something else then it is assumed to be a 'bad' version.
         */
        public void setTested(String tested)
        {
            m_tested = tested;
        }

        /**
         * This method gets the warning messages that should be displayed in case the package doe not match.
         * 
         * @return The warning messages that should be displayed in case the package doe not match.
         */
        public String[] getWarnings()
        {
            return m_warnings;
        }

        /**
         * This method sets the warning messages that should be displayed in case the package doe not match.
         * 
         * @param warnings The warning messages that should be displayed in case the package doe not match.
         */
        public void setWarnings(String[] warnings)
        {
            m_warnings = warnings;
        }

        /**
         * This method gets the regular expression the version should match to be 'OK'.
         * 
         * @return The regular expression the version should match to be 'OK'.
         */
        public String getVersion()
        {
            return m_version;
        }

        /**
         * This method sets the regular expression the version should match to be 'OK'.
         * 
         * @param version The regular expression the version should match to be 'OK'.
         */
        public void setVersion(String version)
        {
            m_version = version;
        }
    }
}
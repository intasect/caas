package org.kisst.cordys.caas.template;

import static org.kisst.cordys.caas.main.Environment.debug;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kisst.cordys.caas.exception.CaasRuntimeException;
import org.kisst.cordys.caas.util.FileUtil;
import org.kisst.cordys.caas.util.StringUtil;

/**
 * Simple template renderer that will perform variable substitution and including of other template files.
 *
 */
public class SimpleRendererImpl implements Renderer
{
    private File templateFolder;

    
    @Override
    public String render(Map<String, String> vars, String template, File templateFolder)
    {
        this.templateFolder = templateFolder;
        
        // First we need to include the files that are to be included using the ${include:file=} or
        // ${include:folder=;pattern=*.xml}
        template = processIncludeFiles(template);
        
        // Now substitute the parameters.
        if (vars != null)
        {
            template = StringUtil.substitute(template, vars);
        }

        // Escape the $ sign.
        return template.replace("${dollar}", "$");
    }

    /**
     * This method will process the includes for the template files. There are 2 possible includes:
     * <ul>
     * <li>${include:file=filename.xml}: The content of the file filename.xml is read. The path is relative to the location of the
     * template XML file.</li>
     * <li>${include:folder=;pattern=*.ctf}: all files in the given folder matching the pattern will be included at the given
     * location. The path is relative to the location of the template XML file.</li>
     * </ul>
     * 
     * @param str The template XML to process.
     * @return The included template XML.
     */
    private String processIncludeFiles(String str)
    {
        String retVal = str;

        Pattern pInclude = Pattern.compile("\\$\\{include\\:(.+?)\\}");
        Pattern pFile = Pattern.compile("file=(.+)");
        Pattern pFolder = Pattern.compile("folder=([^;]+)(;pattern=(.+)){0,1}");

        Matcher m = pInclude.matcher(str);
        if (m.find())
        {
            StringBuffer sb = new StringBuffer(str.length());

            do
            {
                // Parse the include. File or folder. Read the content and append it to the buffer.
                StringBuilder replacement = new StringBuilder(1024);

                Matcher mFile = pFile.matcher(m.group(1));
                Matcher mFolder = pFolder.matcher(m.group(1));

                if (mFile.matches())
                {
                    String filename = mFile.group(1);
                    debug("Found an include of a file. Filename: " + filename);

                    File source = null;
                    if (FileUtil.isAbsolute(filename))
                    {
                        source = new File(filename);
                    }
                    else
                    {
                        source = new File(templateFolder, filename);
                    }

                    if (!FileUtil.doesFileExist(source.getAbsolutePath()))
                    {
                        throw new CaasRuntimeException("File " + source + " does not exist");
                    }

                    // Read the file content
                    replacement.append(processIncludeFiles(FileUtil.loadString(source)));
                }
                else if (mFolder.matches())
                {
                    String filename = mFolder.group(1);
                    String pattern = mFolder.group(3);
                    if (pattern == null || pattern.isEmpty())
                    {
                        pattern = ".+\\.ctf";
                    }

                    debug("Found an include of a folder. Folder: " + filename + " using pattern " + pattern);

                    File source = null;
                    if (FileUtil.isAbsolute(filename))
                    {
                        source = new File(filename);
                    }
                    else
                    {
                        source = new File(templateFolder, filename);
                    }

                    if (!FileUtil.doesFileExist(source.getAbsolutePath()))
                    {
                        throw new CaasRuntimeException("Folder " + source + " does not exist");
                    }

                    if (!source.isDirectory())
                    {
                        throw new CaasRuntimeException("Folder " + source + " is not a folder");
                    }

                    final String actualPattern = "^.*" + pattern + "$";
                    String[] files = source.list(new FilenameFilter() {

                        @Override
                        public boolean accept(File dir, String name)
                        {
                            return name.matches(actualPattern);
                        }
                    });

                    for (String file : files)
                    {
                        debug("Loading file " + new File(source, file).getAbsolutePath());

                        replacement.append(processIncludeFiles(FileUtil.loadString(new File(source, file))));
                    }
                }

                if (replacement.length() > 0)
                {
                    m.appendReplacement(sb, "");
                    sb.append(replacement);
                }
            }
            while (m.find());

            m.appendTail(sb);
            retVal = sb.toString();
        }

        return retVal;
    }
}

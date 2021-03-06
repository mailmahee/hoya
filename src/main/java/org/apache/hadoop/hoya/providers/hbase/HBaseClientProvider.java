/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hoya.providers.hbase;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hoya.HoyaKeys;
import org.apache.hadoop.hoya.api.ClusterDescription;
import org.apache.hadoop.hoya.api.RoleKeys;
import org.apache.hadoop.hoya.exceptions.BadConfigException;
import org.apache.hadoop.hoya.exceptions.HoyaException;
import org.apache.hadoop.hoya.providers.ClientProvider;
import org.apache.hadoop.hoya.tools.ConfigHelper;
import org.apache.hadoop.hoya.tools.HoyaUtils;
import org.apache.hadoop.hoya.tools.YarnUtils;
import org.apache.hadoop.yarn.api.records.LocalResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HBaseClientProvider extends HBaseClusterCore implements
                                                          ClientProvider {

  public static final String ERROR_UNKNOWN_ROLE = "Unknown role ";

  public HBaseClientProvider(Configuration conf) {
    super(conf);
  }

  /**
   * Create the default cluster role instance for a named
   * cluster role; 
   *
   * @param rolename role name
   * @return a node that can be added to the JSON
   */
  @Override
  public Map<String, String> createDefaultClusterRole(String rolename) throws
                                                                       HoyaException {
    Map<String, String> rolemap = new HashMap<String, String>();
    rolemap.put(RoleKeys.ROLE_NAME, rolename);
    String heapSize;
    String infoPort;
    if (rolename.equals(HBaseClusterCore.ROLE_WORKER)) {
      heapSize = DEFAULT_HBASE_WORKER_HEAP;
      infoPort = DEFAULT_HBASE_WORKER_INFOPORT;
    } else if (rolename.equals(HBaseClusterCore.ROLE_MASTER)) {
      heapSize = DEFAULT_HBASE_MASTER_HEAP;
      infoPort = DEFAULT_HBASE_MASTER_INFOPORT;
    } else {
      throw new HoyaException(ERROR_UNKNOWN_ROLE + rolename);
    }
    rolemap.put(RoleKeys.APP_INFOPORT, infoPort);
    rolemap.put(RoleKeys.JVM_HEAP, heapSize);
    return rolemap;
  }


  /**
   * Build the conf dir from the service arguments, adding the hbase root
   * to the FS root dir.
   * This the configuration used by HBase directly
   * @param clusterSpec this is the cluster specification used to define this
   * @return a map of the dynamic bindings for this Hoya instance
   */
  @VisibleForTesting
  public Map<String, String> buildSiteConfFromServiceArguments(ClusterDescription clusterSpec) throws
                                                                                               BadConfigException {

    Map<String, String> master = clusterSpec.getMandatoryRole(ROLE_MASTER);
    
    Map<String, String> worker = clusterSpec.getMandatoryRole(ROLE_WORKER);

    Map<String, String> sitexml = new HashMap<String, String>();

    sitexml.put(HBaseConfigFileOptions.KEY_HBASE_CLUSTER_DISTRIBUTED, "true");
    sitexml.put(HBaseConfigFileOptions.KEY_HBASE_MASTER_PORT, "0");

    sitexml.put(HBaseConfigFileOptions.KEY_HBASE_MASTER_INFO_PORT, master.get(
      RoleKeys.APP_INFOPORT));
    sitexml.put(HBaseConfigFileOptions.KEY_HBASE_ROOTDIR, clusterSpec.hbaseDataPath);
    sitexml.put(HBaseConfigFileOptions.KEY_REGIONSERVER_INFO_PORT,
                worker.get(RoleKeys.APP_INFOPORT));
    sitexml.put(HBaseConfigFileOptions.KEY_REGIONSERVER_PORT,"0");
    sitexml.put(HBaseConfigFileOptions.KEY_ZNODE_PARENT, clusterSpec.zkPath);
    sitexml.put(HBaseConfigFileOptions.KEY_ZOOKEEPER_PORT,
                Integer.toString(clusterSpec.zkPort));
    sitexml.put(HBaseConfigFileOptions.KEY_ZOOKEEPER_QUORUM, clusterSpec.zkHosts);
    return sitexml;
  }


  /**
   * This builds up the site configuration for the AM and downstream services;
   * the path is added to the cluster spec so that launchers in the 
   * AM can pick it up themselves. 
   * @param clusterFS filesystem
   * @param serviceConf conf used by the service
   * @param clusterSpec cluster specification
   * @param originConfDirPath the original config dir -treat as read only
   * @param generatedConfDirPath path to place generated artifacts
   * @return a map of name to local resource to add to the AM launcher
   */
  @Override
  public Map<String, LocalResource> prepareAMAndConfigForLaunch(FileSystem clusterFS,
                                                                Configuration serviceConf,
                                                                ClusterDescription clusterSpec,
                                                                Path originConfDirPath,
                                                                Path generatedConfDirPath) throws
                                                                        IOException,
                                                                        BadConfigException {
    Configuration templateConf = ConfigHelper.loadTemplateConfiguration(
      serviceConf,
      originConfDirPath,
      HBaseCommands.HBASE_TEMPLATE,
      HBaseCommands.HBASE_TEMPLATE_RESOURCE);

    //construct the cluster configuration values
    Map<String, String> clusterConfMap = buildSiteConfFromServiceArguments(
      clusterSpec);
    //merge them
    ConfigHelper.addConfigMap(templateConf, clusterConfMap);

    if (log.isDebugEnabled()) {
      ConfigHelper.dumpConf(templateConf);
    }

    Path sitePath = ConfigHelper.generateConfig(serviceConf,
                                                templateConf,
                                                generatedConfDirPath,
                                                HBaseCommands.HBASE_SITE);

    log.debug("Saving the config to {}", sitePath);
    Map<String, LocalResource> confResources;
    confResources = YarnUtils.submitDirectory(clusterFS,
                                              generatedConfDirPath,
                                              HoyaKeys.PROPAGATED_CONF_DIR_NAME);
    return confResources;
  }
}

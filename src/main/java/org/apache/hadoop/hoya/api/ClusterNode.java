/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.hadoop.hoya.api;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Describe a specific node in the cluster
 */
@JsonIgnoreProperties(ignoreUnknown = true)

public class ClusterNode {
  protected static final Logger
    LOG = LoggerFactory.getLogger(ClusterDescription.class);
  
  @JsonIgnore
  public ContainerId containerId;
  /**
   * server name
   */
  public String name;

  public String role;
  /**
   * state (Currently arbitrary text)
   */
  public int state;

  /**
   * Exit code: only valid if the state >= STOPPED
   */
  public int exitCode;

  /**
   * what was the command executed?
   */
  public String command;

  /**
   * Any diagnostics
   */
  public String diagnostics;

  /**
   * What is the tail output from the executed process (or [] if not started
   * or the log cannot be picked up
   */
  public String[] output;

  /**
   * Any environment details
   */
  public String[] environment;

  public String uuid;

  public ClusterNode(String name) {
    this.name = name;
  }

  public ClusterNode() {
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(name).append(": ").append(state).append("\n");
    builder.append("role: ").append(role).append("\n");
    builder.append("uuid: ").append(uuid).append("\n");
    builder.append(command).append("\n");
    if (output != null) {
      for (String line : output) {
        builder.append(line).append("\n");
      }
    }
    if (diagnostics != null) {
      builder.append(diagnostics).append("\n");
    }
    return builder.toString();
  }

  /**
   * Convert to a JSON string
   * @return a JSON string description
   * @throws IOException Problems mapping/writing the object
   */
  public String toJsonString() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(this);
  }


  /**
   * Convert from JSON
   * @param json input
   * @return the parsed JSON
   * @throws IOException IO
   */
  public static ClusterNode fromJson(String json)
    throws IOException, JsonParseException, JsonMappingException {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readValue(json, ClusterNode.class);
    } catch (IOException e) {
      LOG.error("Exception while parsing json : " + e + "\n" + json, e);
      throw e;
    }
  }
  
}

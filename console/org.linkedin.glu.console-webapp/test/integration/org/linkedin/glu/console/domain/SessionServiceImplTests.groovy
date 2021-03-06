/*
 * Copyright (c) 2011 Yan Pujante
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.linkedin.glu.console.domain

import org.linkedin.glu.orchestration.engine.session.UserSession
import org.linkedin.glu.orchestration.engine.delta.DeltaServiceImpl
import org.linkedin.glu.orchestration.engine.delta.UserCustomDeltaDefinition
import org.linkedin.glu.orchestration.engine.delta.CustomDeltaDefinition
import org.linkedin.groovy.util.json.JsonUtils
import org.linkedin.glu.orchestration.engine.delta.CustomDeltaDefinitionSerializer
import org.linkedin.glu.orchestration.engine.session.SessionServiceImpl
import org.linkedin.glu.orchestration.engine.authorization.AuthorizationService

/**
 * @author yan@pongasoft.com */
public class SessionServiceImplTests extends GroovyTestCase
{
  public static final String DCDN = DeltaServiceImpl.DEFAULT_CUSTOM_DELTA_DEFINITION_NAME

  CustomDeltaDefinitionSerializer customDeltaDefinitionSerializer
  SessionServiceImpl sessionService
  DeltaServiceImpl deltaService

  // will hold the executing principal
  String executingPrincipal = 'user1'

  def cdd = [
    name: 'd1',
    description: 'desc1',
    errorsOnly: false,
    summary: true,
    columnsDefinition: [
      [
        name: 'ag',
        source: 'agent',
        orderBy: 'asc'
      ],
      [
        name: 'mp',
        source: 'mountPoint'
      ],
      [
        name: 'vs',
        source: 'metadata.version'
      ],
      [
        name: 'oldest',
        source: 'metadata.modifiedTime',
        groupBy: 'min'
      ],
      [
        name: 'newest',
        source: 'metadata.modifiedTime',
        groupBy: 'max'
      ],
    ]
  ]

  @Override
  protected void setUp()
  {
    super.setUp()

    def authorizationService = [
      getExecutingPrincipal: {
        return executingPrincipal
      }
    ]

    sessionService.authorizationService = authorizationService as AuthorizationService
    deltaService.authorizationService = authorizationService as AuthorizationService
  }

  /**
   * Test for session behavior
   */
  void testSession()
  {
    // no entry
    assertNull(deltaService.findUserCustomDeltaDefinitionByName(DCDN))

    // 'user1' has no default => it will return the global default
    UserSession default1 = sessionService.findUserSession('dashboard1')
    assertEquals('user1', default1.username)
    assertEquals(DCDN, default1.customDeltaDefinition.name)

    // this verifies that a new entry in the database has been created
    UserCustomDeltaDefinition default2 =
      deltaService.findUserCustomDeltaDefinitionByName(DCDN)
    assertNotNull(default2)

    // user2 has a <default> entry
    executingPrincipal = 'user2'
    assertNull(deltaService.findUserCustomDeltaDefinitionByName(DCDN))

    CustomDeltaDefinition definition = toCustomDeltaDefinition(cdd)
    definition.name = DCDN
    definition.description = 'user2-<default>'
    UserCustomDeltaDefinition ucdd =
      new UserCustomDeltaDefinition(username: 'user2',
                                    customDeltaDefinition: definition)
    assertTrue(deltaService.saveUserCustomDeltaDefinition(ucdd))

    // verifying that the <default> entry was used
    default1 = sessionService.findUserSession('dashboard1')
    assertEquals('user2', default1.username)
    assertEquals(DCDN, default1.customDeltaDefinition.name)
    assertEquals("user2-<default>", default1.customDeltaDefinition.description)

    // user3 has a dashboard1 entry
    executingPrincipal = 'user3'
    assertNull(deltaService.findUserCustomDeltaDefinitionByName(DCDN))

    definition = toCustomDeltaDefinition(cdd)
    definition.name = 'dashboard1'
    definition.description = 'user3-dashboard1'
    ucdd =
      new UserCustomDeltaDefinition(username: 'user3',
                                    customDeltaDefinition: definition)
    assertTrue(deltaService.saveUserCustomDeltaDefinition(ucdd))

    // verifying that the dashboard1 entry was used
    default1 = sessionService.findUserSession('dashboard1')
    assertEquals('user3', default1.username)
    assertEquals('dashboard1', default1.customDeltaDefinition.name)
    assertEquals("user3-dashboard1", default1.customDeltaDefinition.description)

    // no default entry was created
    assertNull(deltaService.findUserCustomDeltaDefinitionByName(DCDN))

    // session still active... no change
    default1 = sessionService.findUserSession('dashboard2')
    assertEquals('user3', default1.username)
    assertEquals('dashboard1', default1.customDeltaDefinition.name)
    assertEquals("user3-dashboard1", default1.customDeltaDefinition.description)

    // clearing the definition
    sessionService.clearUserSession()

    // dashboard2 does not exist and <default> does not exist either... will be created
    default1 = sessionService.findUserSession('dashboard2')
    assertEquals('user3', default1.username)
    assertEquals(DCDN, default1.customDeltaDefinition.name)
    assertNotNull(deltaService.findUserCustomDeltaDefinitionByName(DCDN))
  }

  CustomDeltaDefinition toCustomDeltaDefinition(LinkedHashMap<String, Serializable> cdd)
  {
    return customDeltaDefinitionSerializer.deserialize(JsonUtils.toJSON(cdd).toString(),
                                                       customDeltaDefinitionSerializer.contentVersion)
  }
}
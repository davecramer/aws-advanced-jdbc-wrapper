/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package software.amazon.jdbc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.jdbc.HostSpec;

class ConnectionUrlParserTest {
  @ParameterizedTest
  @MethodSource("testGetHostsFromConnectionUrlArguments")
  void testGetHostsFromConnectionUrl_returnCorrectHostList(String testUrl, List<HostSpec> expected) {
    final ConnectionUrlParser parser = new ConnectionUrlParser();
    final List<HostSpec> results = parser.getHostsFromConnectionUrl(testUrl);

    assertEquals(expected.size(), results.size());
    for (int i = 0; i < expected.size(); i++) {
      assertEquals(expected.get(i), results.get(i));
    }
  }

  @ParameterizedTest
  @MethodSource("userUrls")
  void testParseUserFromUrl(final String url, final String expectedUser) {
    final String actualUser = ConnectionUrlParser.parseUserFromUrl(url);
    assertEquals(expectedUser, actualUser);
  }

  @ParameterizedTest
  @MethodSource("passwordUrls")
  void testParsePasswordFromUrl(final String url, final String expected) {
    final String actual = ConnectionUrlParser.parsePasswordFromUrl(url);
    assertEquals(expected, actual);
  }

  private static Stream<Arguments> testGetHostsFromConnectionUrlArguments() {
    return Stream.of(
        Arguments.of("protocol//", new ArrayList<HostSpec>()),
        Arguments.of("bar/", new ArrayList<HostSpec>()),
        Arguments.of("invalid-hosts?", new ArrayList<HostSpec>()),
        Arguments.of("jdbc//host:3303/db?param=1", Collections.singletonList(new HostSpec("host", 3303))),
        Arguments.of("protocol//host2:3303", Collections.singletonList(new HostSpec("host2", 3303))),
        Arguments.of("foo//host:3303/?#", Collections.singletonList(new HostSpec("host", 3303))),
        Arguments.of("jdbc:mysql:replication://host:badInt?param=",
            Collections.singletonList(new HostSpec("host"))),
        Arguments.of("jdbc:driver:test://source,replica1:3303,host/test",
            Arrays.asList(new HostSpec("source"), new HostSpec("replica1", 3303),
                new HostSpec("host")))
    );
  }

  private static Stream<Arguments> userUrls() {
    return Stream.of(
        Arguments.of("protocol//url/db?user=foo", "foo"),
        Arguments.of("protocol//url/db?user=foo&pass=bar", "foo"),
        Arguments.of("protocol//url/db?USER=foo", null),
        Arguments.of("protocol//url/db?USER=foo&pass=bar", null),
        Arguments.of("protocol//url/db?username=foo", null)
    );
  }

  private static Stream<Arguments> passwordUrls() {
    return Stream.of(
        Arguments.of("protocol//url/db?password=foo", "foo"),
        Arguments.of("protocol//url/db?password=foo&user=bar", "foo"),
        Arguments.of("protocol//url/db?PASSWORD=foo", null),
        Arguments.of("protocol//url/db?PASSWORD=foo&user=bar", null),
        Arguments.of("protocol//url/db?pass=foo", null)
    );
  }
}

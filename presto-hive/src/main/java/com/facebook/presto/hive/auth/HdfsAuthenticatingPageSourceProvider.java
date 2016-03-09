/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.presto.hive.auth;

import com.facebook.presto.hive.ForHdfs;
import com.facebook.presto.hive.HivePageSourceProvider;
import com.facebook.presto.spi.ColumnHandle;
import com.facebook.presto.spi.ConnectorPageSource;
import com.facebook.presto.spi.ConnectorPageSourceProvider;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorSplit;
import com.facebook.presto.spi.RecordPageSource;

import javax.inject.Inject;

import java.util.List;

public class HdfsAuthenticatingPageSourceProvider
        implements ConnectorPageSourceProvider
{
    private final HadoopAuthentication authentication;
    private final HivePageSourceProvider targetConnectorPageSourceProvider;

    @Inject
    public HdfsAuthenticatingPageSourceProvider(@ForHdfs HadoopAuthentication authentication, HivePageSourceProvider targetConnectorPageSourceProvider)
    {
        this.authentication = authentication;
        this.targetConnectorPageSourceProvider = targetConnectorPageSourceProvider;
    }

    @Override
    public ConnectorPageSource createPageSource(ConnectorSession session, ConnectorSplit split, List<ColumnHandle> columns)
    {
        return authentication.doAs(session.getUser(), () -> {
            ConnectorPageSource targetPageSource = targetConnectorPageSourceProvider.createPageSource(session, split, columns);
            if (targetPageSource instanceof RecordPageSource) {
                return new HdfsAuthenticatingRecordPageSource(session, authentication, (RecordPageSource) targetPageSource);
            }
            else {
                return new HdfsAuthenticatingPageSource(session, authentication, targetPageSource);
            }
        });
    }
}

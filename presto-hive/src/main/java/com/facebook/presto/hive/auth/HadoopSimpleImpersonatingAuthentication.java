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

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.hadoop.security.UserGroupInformation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class HadoopSimpleImpersonatingAuthentication
        implements HadoopAuthentication
{
    private LoadingCache<String, UserGroupInformation> userGroupInformationCache = CacheBuilder.newBuilder().build(
            new CacheLoader<String, UserGroupInformation>()
            {
                @Override
                public UserGroupInformation load(@NotNull String user)
                        throws Exception
                {
                    return UserGroupInformation.createProxyUser(user, getUserGroupInformation());
                }
            });

    @Override
    public void authenticate()
    {
        // noop
    }

    @Override
    public UserGroupInformation getUserGroupInformation()
    {
        try {
            return UserGroupInformation.getCurrentUser();
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public UserGroupInformation getUserGroupInformation(String user)
    {
        return userGroupInformationCache.getUnchecked(user);
    }
}

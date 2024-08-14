/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
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
package org.thingsboard.server.dao.oauth2;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.OAuth2ClientId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.oauth2.OAuth2Client;
import org.thingsboard.server.common.data.oauth2.OAuth2ClientInfo;
import org.thingsboard.server.common.data.oauth2.OAuth2ClientLoginInfo;
import org.thingsboard.server.common.data.oauth2.PlatformType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.eventsourcing.DeleteEntityEvent;
import org.thingsboard.server.dao.eventsourcing.SaveEntityEvent;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.Validator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;
import static org.thingsboard.server.dao.service.Validator.validateString;

@Slf4j
@Service("OAuth2ClientService")
public class OAuth2ClientServiceImpl extends AbstractEntityService implements OAuth2ClientService {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_CLIENT_REGISTRATION_ID = "Incorrect clientRegistrationId ";
    public static final String INCORRECT_DOMAIN_NAME = "Incorrect domainName ";

    @Autowired
    private OAuth2ClientDao oauth2ClientDao;
    @Autowired
    private DataValidator<OAuth2Client> oAuth2ClientDataValidator;

    @Override
    public List<OAuth2ClientLoginInfo> findOAuth2ClientLoginInfosByDomainName(String domainName) {
        log.trace("Executing findOAuth2ClientLoginInfosByDomainName [{}] ", domainName);
        validateString(domainName, dn -> INCORRECT_DOMAIN_NAME + dn);
        return oauth2ClientDao.findEnabledByDomainName(domainName)
                .stream()
                .map(OAuth2Utils::toClientLoginInfo)
                .collect(Collectors.toList());
    }

    @Override
    public List<OAuth2ClientLoginInfo> findOAuth2ClientLoginInfosByMobilePkgNameAndPlatformType(String pkgName, PlatformType platformType) {
        log.trace("Executing findOAuth2ClientLoginInfosByMobilePkgNameAndPlatformType pkgName=[{}] platformType=[{}]",pkgName, platformType);
        return oauth2ClientDao.findEnabledByPckNameAndPlatformType(pkgName, platformType)
                .stream()
                .map(OAuth2Utils::toClientLoginInfo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OAuth2Client saveOAuth2Client(TenantId tenantId, OAuth2Client oAuth2Client) {
        log.trace("Executing saveOAuth2Client [{}]", oAuth2Client);
        oAuth2ClientDataValidator.validate(oAuth2Client, OAuth2Client::getTenantId);
        OAuth2Client savedOauth2Client = oauth2ClientDao.save(tenantId, oAuth2Client);
        eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId).entityId(savedOauth2Client.getId()).entity(savedOauth2Client).build());
        return savedOauth2Client;
    }

    @Override
    public OAuth2Client findOAuth2ClientById(TenantId tenantId, OAuth2ClientId oAuth2ClientId) {
        log.trace("Executing findOAuth2ClientById [{}]", oAuth2ClientId);
        validateId(oAuth2ClientId, uuid -> INCORRECT_CLIENT_REGISTRATION_ID + uuid);
        return oauth2ClientDao.findById(tenantId, oAuth2ClientId.getId());
    }

    @Override
    public List<OAuth2Client> findOAuth2ClientsByTenantId(TenantId tenantId) {
        log.trace("Executing findOAuth2ClientsByTenantId [{}]", tenantId);
        return oauth2ClientDao.findByTenantId(tenantId.getId(), new PageLink(Integer.MAX_VALUE)).getData();
    }

    @Override
    public String findAppSecret(UUID id, String pkgName) {
        log.trace("Executing findAppSecret [{}][{}]", id, pkgName);
        validateId(id, uuid -> INCORRECT_CLIENT_REGISTRATION_ID + uuid);
        validateString(pkgName, "Incorrect package name");
        return oauth2ClientDao.findAppSecret(id, pkgName);
    }

    @Override
    @Transactional
    public void deleteOAuth2ClientById(TenantId tenantId, OAuth2ClientId oAuth2ClientId) {
        log.trace("Executing deleteOAuth2ClientById [{}]", oAuth2ClientId);
        oauth2ClientDao.removeById(tenantId, oAuth2ClientId.getId());
        eventPublisher.publishEvent(DeleteEntityEvent.builder()
                .tenantId(tenantId)
                .entityId(oAuth2ClientId)
                .build());

    }

    @Override
    public void deleteOauth2ClientsByTenantId(TenantId tenantId) {
        log.trace("Executing deleteOauth2ClientsByTenantId, tenantId [{}]", tenantId);
        Validator.validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        oauth2ClientDao.deleteByTenantId(tenantId);
    }

    @Override
    public PageData<OAuth2ClientInfo> findOAuth2ClientInfosByTenantId(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findOAuth2ClientInfosByTenantId tenantId=[{}]", tenantId);
        PageData<OAuth2Client> clientInfos = oauth2ClientDao.findByTenantId(tenantId.getId(), pageLink);
        List<OAuth2ClientInfo> oAuth2ClientInfos = clientInfos
                .getData()
                .stream()
                .map(OAuth2ClientInfo::new)
                .collect(Collectors.toList());
        return new PageData<>(oAuth2ClientInfos, clientInfos.getTotalPages(), clientInfos.getTotalPages(), clientInfos.hasNext());
    }

    @Override
    public List<OAuth2ClientInfo> findOAuth2ClientInfosByIds(TenantId tenantId, List<OAuth2ClientId> oAuth2ClientIds) {
        log.trace("Executing findQueueStatsByIds, tenantId [{}], queueStatsIds [{}]", tenantId, oAuth2ClientIds);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateIds(oAuth2ClientIds, ids -> "Incorrect clientIds " + ids);
        return oauth2ClientDao.findByIds(tenantId, oAuth2ClientIds)
                .stream()
                .map(OAuth2ClientInfo::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isPropagateOAuth2ClientToEdge(TenantId tenantId, OAuth2ClientId oAuth2ClientId) {
        log.trace("Executing isPropagateOAuth2ClientToEdge, tenantId [{}], oAuth2ClientId [{}]", tenantId, oAuth2ClientId);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateId(oAuth2ClientId, uuid -> INCORRECT_CLIENT_REGISTRATION_ID + uuid);
        return oauth2ClientDao.isPropagateToEdge(tenantId, oAuth2ClientId.getId());
    }

    @Override
    public void deleteByTenantId(TenantId tenantId) {
        deleteOauth2ClientsByTenantId(tenantId);
    }

    @Override
    public Optional<HasId<?>> findEntity(TenantId tenantId, EntityId entityId) {
        return Optional.ofNullable(findOAuth2ClientById(tenantId, new OAuth2ClientId(entityId.getId())));
    }

    @Override
    @Transactional
    public void deleteEntity(TenantId tenantId, EntityId id, boolean force) {
        OAuth2Client oAuth2Client = oauth2ClientDao.findById(tenantId, id.getId());
        if (oAuth2Client == null) {
            return;
        }
        deleteOAuth2ClientById(tenantId, oAuth2Client.getId());
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.OAUTH2_CLIENT;
    }

}

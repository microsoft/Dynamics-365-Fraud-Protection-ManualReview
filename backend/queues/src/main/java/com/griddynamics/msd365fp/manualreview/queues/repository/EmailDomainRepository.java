// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.repository;

import com.griddynamics.msd365fp.manualreview.queues.model.persistence.EmailDomain;
import com.azure.spring.data.cosmos.repository.CosmosRepository;

public interface EmailDomainRepository extends CosmosRepository<EmailDomain, String> {

    Iterable<EmailDomain> findByEmailDomainName(String emailDomainName);

}

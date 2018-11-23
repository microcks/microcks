export class ImportJob {
  id: string;
  name: string;
  repositoryUrl: string;
  repositoryDisableSSLValidation: boolean = false;
  frequency: string;
  createdDate: Date;
  lastImportDate: Date;
  lastImportError: string;
  active: boolean = false;
  etag: string;

  secretRef: SecretRef;
  serviceRefs: ServiceRef[];
}

export class ServiceRef {
  serviceId: string;
  name: string;
  version: string;
}

export class Secret {
  id: string;
  name: string;
  description: string;
  username: string;
  password: string;
  token: string;
  tokenHeader: string;
  caCertPem: string;
}

export class SecretRef {
  secretId: string;
  name: string;
  
  constructor(secretId: string, name: string) {
    this.secretId = secretId;
    this.name = name;
  }
}
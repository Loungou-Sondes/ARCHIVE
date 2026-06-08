import { CommonModule } from '@angular/common';

import { HttpClient } from '@angular/common/http';

import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';

import { FormsModule } from '@angular/forms';

import { ButtonModule } from 'primeng/button';

import { InputTextModule } from 'primeng/inputtext';

import { RippleModule } from 'primeng/ripple';

import { AuthService } from '../../core/auth/auth.service';

import { AppTranslateService } from '../../core/i18n/app-translate.service';

import { environment } from '../../../environments/environment';

import { TranslocoPipe } from '@jsverse/transloco';



interface UserProfileResponse {

  userName: string | null;

  email: string | null;

  phoneNumber: string | null;

  role: string | null;

}



interface UpdateUserProfileRequest {

  email: string | null;

  phoneNumber: string | null;

}



@Component({

  selector: 'app-profile',

  standalone: true,

  imports: [CommonModule, FormsModule, ButtonModule, InputTextModule, RippleModule, TranslocoPipe],

  templateUrl: './profile.component.html',

  styleUrl: './profile.component.scss',

})

export class ProfileComponent implements OnInit {

  private readonly http = inject(HttpClient);

  private readonly authService = inject(AuthService);

  private readonly i18n = inject(AppTranslateService);

  private readonly cdr = inject(ChangeDetectorRef);



  isEditing = false;

  loading = false;

  saving = false;

  successMessage = '';

  errorMessage = '';



  profile: UserProfileResponse = {

    userName: null,

    email: null,

    phoneNumber: null,

    role: null,

  };



  editedProfile: UserProfileResponse = structuredClone(this.profile);



  ngOnInit(): void {

    this.loadProfile();

  }



  loadProfile(): void {

    this.loading = true;

    this.http

      .get<UserProfileResponse>(`${environment.apiUrl}/api/auth/profile`)

      .subscribe({

        next: (data) => {

          this.profile = {

            userName: data.userName,

            email: data.email,

            phoneNumber: data.phoneNumber,

            role: data.role,

          };

          this.editedProfile = structuredClone(this.profile);

          this.loading = false;

          this.cdr.detectChanges();

        },

        error: () => {

          this.loading = false;

          this.errorMessage = this.i18n.t('profile.loadError');

          this.cdr.detectChanges();

        },

      });

  }



  toggleEdit(): void {

    this.successMessage = '';

    this.errorMessage = '';

    this.isEditing = !this.isEditing;

    if (this.isEditing) {

      this.editedProfile = structuredClone(this.profile);

    }

  }



  saveProfile(): void {

    const payload: UpdateUserProfileRequest = {

      email: this.toNullable(this.editedProfile.email),

      phoneNumber: this.toNullable(this.editedProfile.phoneNumber),

    };



    this.saving = true;

    this.successMessage = '';

    this.errorMessage = '';

    this.http

      .put<UserProfileResponse>(`${environment.apiUrl}/api/auth/profile`, payload)

      .subscribe({

        next: (updated) => {

          this.profile = {

            userName: updated.userName,

            email: updated.email,

            phoneNumber: updated.phoneNumber,

            role: updated.role,

          };

          this.editedProfile = structuredClone(this.profile);

          this.isEditing = false;

          this.saving = false;

          this.successMessage = this.i18n.t('profile.saveSuccess');

          this.cdr.detectChanges();

        },

        error: (err) => {

          this.saving = false;

          this.errorMessage = this.i18n.apiErrorDetail(err, 'profile.saveError');

          this.cdr.detectChanges();

        },

      });

  }



  handleImageError(event: Event): void {

    const img = event.target as HTMLImageElement;

    img.src = 'assets/images/profil.png';

  }



  displayRole(): string {

    const dbRole = this.profile.role?.trim();

    if (dbRole) {

      return dbRole;

    }

    return this.authService.displayRole();

  }



  private toNullable(value: string | null): string | null {

    if (value === null || value === undefined) {

      return null;

    }



    const trimmed = value.trim();

    return trimmed.length > 0 ? trimmed : null;

  }

}


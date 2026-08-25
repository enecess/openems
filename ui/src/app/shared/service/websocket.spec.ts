// @ts-strict-ignore
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
import { ToastController } from "@ionic/angular";
import { TranslateService } from "@ngx-translate/core";
import { CookieService } from "ngx-cookie-service";
import { NgxSpinnerService } from "ngx-spinner";
import { EMPTY } from "rxjs";

import { PlatFormService } from "src/app/platform.service";

import { States } from "../ngrx-store/states";
import { AuthService } from "./auth/auth.service";
import { OAuthService } from "./auth/oauth.service";
import { JsonrpcRequestStateHandler } from "./jsonrpc/jsonrpc-request-state-handler";
import { Pagination } from "./pagination";
import { RouteService } from "./route.service";
import { Service } from "./service";
import { UserService } from "./user.service";
import { Websocket } from "./websocket";

describe("Websocket", () => {
    let cookieService: CookieService;
    let router: jasmine.SpyObj<Router>;
    let websocket: Websocket;

    beforeEach(() => {
        spyOn(Websocket.prototype, "connect");

        router = jasmine.createSpyObj<Router>("Router", ["navigate"]);
        TestBed.configureTestingModule({
            providers: [
                Websocket,
                Service,
                AuthService,
                CookieService,
                { provide: JsonrpcRequestStateHandler, useValue: {} },
                { provide: NgxSpinnerService, useValue: {} },
                { provide: OAuthService, useValue: { startOAuth: jasmine.createSpy() } },
                { provide: Pagination, useValue: {} },
                {
                    provide: PlatFormService,
                    useValue: { getDevice: () => ({ isSmartphone: () => false }) },
                },
                { provide: RouteService, useValue: {} },
                { provide: Router, useValue: router },
                {
                    provide: ToastController,
                    useValue: {},
                },
                {
                    provide: TranslateService,
                    useValue: {
                        addLangs: jasmine.createSpy(),
                        onLangChange: EMPTY,
                        setFallbackLang: jasmine.createSpy(),
                    },
                },
                { provide: UserService, useValue: {} },
            ],
        });

        websocket = TestBed.inject(Websocket);
        TestBed.inject(AuthService);
        cookieService = TestBed.inject(CookieService);
    });

    afterEach(() => {
        cookieService.delete(AuthService.TOKEN, "/");
        cookieService.delete(OAuthService.REFRESH_TOKEN, "/");
        cookieService.delete("oauthredirectstate");
    });

    it("clears the local session when the logout request is rejected", async () => {
        const requestError = new Error("logout rejected");
        cookieService.set(AuthService.TOKEN, "session-token", { path: "/" });
        cookieService.set(OAuthService.REFRESH_TOKEN, "refresh-token", { path: "/" });
        websocket.state.set(States.AUTHENTICATED);
        spyOn(websocket, "sendRequest").and.rejectWith(requestError);
        const consoleError = spyOn(console, "error");

        websocket.logout();
        await Promise.resolve();
        await Promise.resolve();

        expect(cookieService.check(AuthService.TOKEN)).toBeFalse();
        expect(cookieService.check(OAuthService.REFRESH_TOKEN)).toBeFalse();
        expect(websocket.state()).toBe(States.NOT_AUTHENTICATED);
        expect(consoleError).toHaveBeenCalledOnceWith(requestError);
        expect(router.navigate).toHaveBeenCalledOnceWith(["/login"]);
    });
});

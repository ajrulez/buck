/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.apple;

import com.facebook.buck.cxx.CxxCompilationDatabase;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.Linker;
import com.facebook.buck.cxx.TypeAndPlatform;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.util.Optionals;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;

public class AppleLibraryDescription implements
    Description<AppleNativeTargetDescriptionArg>, Flavored {
  public static final BuildRuleType TYPE = BuildRuleType.of("apple_library");

  private static final Set<Flavor> SUPPORTED_FLAVORS = ImmutableSet.of(
      CxxCompilationDatabase.COMPILATION_DATABASE,
      CxxDescriptionEnhancer.HEADER_SYMLINK_TREE_FLAVOR,
      CxxDescriptionEnhancer.EXPORTED_HEADER_SYMLINK_TREE_FLAVOR,
      CxxDescriptionEnhancer.STATIC_FLAVOR,
      CxxDescriptionEnhancer.SHARED_FLAVOR,
      ImmutableFlavor.of("default"));

  private static final Predicate<Flavor> IS_SUPPORTED_FLAVOR = new Predicate<Flavor>() {
    @Override
    public boolean apply(Flavor flavor) {
      return SUPPORTED_FLAVORS.contains(flavor);
    }
  };

  private final CxxLibraryDescription delegate;
  private final FlavorDomain<CxxPlatform> cxxPlatformFlavorDomain;
  private final ImmutableMap<Flavor, AppleCxxPlatform> platformFlavorsToAppleCxxPlatforms;

  public AppleLibraryDescription(
      CxxLibraryDescription delegate,
      FlavorDomain<CxxPlatform> cxxPlatformFlavorDomain,
      Map<Flavor, AppleCxxPlatform> platformFlavorsToAppleCxxPlatforms) {
    this.delegate = delegate;
    this.cxxPlatformFlavorDomain = cxxPlatformFlavorDomain;
    this.platformFlavorsToAppleCxxPlatforms =
        ImmutableMap.copyOf(platformFlavorsToAppleCxxPlatforms);
  }

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public AppleNativeTargetDescriptionArg createUnpopulatedConstructorArg() {
    return new AppleNativeTargetDescriptionArg();
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    return FluentIterable.from(flavors).allMatch(IS_SUPPORTED_FLAVOR) ||
        delegate.hasFlavors(flavors);
  }

  @Override
  public <A extends AppleNativeTargetDescriptionArg> BuildRule createBuildRule(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) {
    return createBuildRule(params, resolver, args, Optional.<Linker.LinkableDepType>absent());
  }

  public <A extends AppleNativeTargetDescriptionArg> BuildRule createBuildRule(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args,
      Optional<Linker.LinkableDepType> linkableDepType) {
    SourcePathResolver pathResolver = new SourcePathResolver(resolver);

    CxxLibraryDescription.Arg delegateArg = delegate.createUnpopulatedConstructorArg();
    TypeAndPlatform typeAndPlatform =
        CxxLibraryDescription.getTypeAndPlatform(
            params.getBuildTarget(),
            cxxPlatformFlavorDomain);
    Optional<AppleCxxPlatform> appleCxxPlatform = Optionals.bind(
        typeAndPlatform.getPlatform(),
        new Function<Map.Entry<Flavor, CxxPlatform>, Optional<AppleCxxPlatform>>() {
          @Override
          public Optional<AppleCxxPlatform> apply(Map.Entry<Flavor, CxxPlatform> input) {
            return Optional.fromNullable(platformFlavorsToAppleCxxPlatforms.get(input.getKey()));
          }
        });
    Optional<AppleSdkPaths> appleSdkPaths = appleCxxPlatform.transform(
        AppleCxxPlatform.TO_APPLE_SDK_PATHS);
    AppleDescriptions.populateCxxLibraryDescriptionArg(
        pathResolver,
        delegateArg,
        args,
        params.getBuildTarget(),
        appleSdkPaths,
        !isSharedLibraryTarget(params.getBuildTarget()));

    return delegate.createBuildRule(
        params,
        resolver,
        delegateArg,
        typeAndPlatform,
        linkableDepType);
  }

  public static boolean isSharedLibraryTarget(BuildTarget target) {
    return target.getFlavors().contains(CxxDescriptionEnhancer.SHARED_FLAVOR);
  }
}
